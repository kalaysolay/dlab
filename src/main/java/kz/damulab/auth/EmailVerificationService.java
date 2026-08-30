package kz.damulab.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

/**
 * Управляет жизненным циклом подтверждения email: выпуском, повторной отправкой и погашением токена.
 * Сырые токены существуют только в памяти на время формирования письма; поиск выполняется по SHA-256.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final EmailVerificationProperties properties;
    private final EmailVerificationTokenRepository tokens;
    private final EmailDeliveryAttemptRepository deliveryAttempts;
    private final AppUserRepository users;
    private final VerificationEmailSender emailSender;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            EmailVerificationProperties properties,
            EmailVerificationTokenRepository tokens,
            EmailDeliveryAttemptRepository deliveryAttempts,
            AppUserRepository users,
            VerificationEmailSender emailSender,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.tokens = tokens;
        this.deliveryAttempts = deliveryAttempts;
        this.users = users;
        this.emailSender = emailSender;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Фиксирует первичный токен в БД до обращения к SMTP.BZ. Сбой доставки возвращается
     * нормализованным статусом и не откатывает созданный аккаунт.
     */
    public EmailDeliveryAttemptStatus sendInitialVerification(Long userId) {
        if (!properties.isEnabled()) {
            return null;
        }
        IssuedVerification issued = issue(userId, false);
        return issued == null ? null : sendAndRecord(issued);
    }

    /**
     * Активирует пользователя по действующему одноразовому токену.
     *
     * @return точный внутренний итог, который контроллер преобразует в безопасное сообщение
     */
    public EmailVerificationResult confirm(String rawToken) {
        if (!properties.isEnabled()
                || rawToken == null
                || rawToken.length() != 43
                || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            return EmailVerificationResult.INVALID;
        }
        return transactions.execute(status -> {
            EmailVerificationToken token = tokens.findByTokenHashForUpdate(hash(rawToken)).orElse(null);
            if (token == null) {
                return EmailVerificationResult.INVALID;
            }
            if (token.getStatus() == EmailVerificationStatus.VERIFIED) {
                return EmailVerificationResult.INVALID;
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (token.isExpiredAt(now)) {
                token.markExpired(now);
                return EmailVerificationResult.EXPIRED;
            }
            token.getUser().confirmEmail(now);
            token.markVerified(now);
            return EmailVerificationResult.VERIFIED;
        });
    }

    /**
     * Повторно отправляет письмо для неактивного аккаунта. Результат намеренно не раскрывает,
     * существует ли адрес; частые запросы внутри {@code resend-cooldown} молча пропускаются.
     */
    public EmailDeliveryAttemptStatus resend(String email) {
        if (!properties.isEnabled() || email == null || email.isBlank()) {
            return null;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Long userId = transactions.execute(status -> users.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> !user.isEnabled())
                .map(AppUser::getId)
                .orElse(null));
        if (userId == null) {
            return null;
        }
        IssuedVerification issued = issue(userId, true);
        return issued == null ? null : sendAndRecord(issued);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    private IssuedVerification issue(Long userId, boolean enforceCooldown) {
        return transactions.execute(status -> {
            AppUser user = users.findById(userId).orElse(null);
            if (user == null || user.isEnabled()) {
                return null;
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            EmailVerificationToken token = tokens.findByUserIdForUpdate(userId).orElse(null);
            if (enforceCooldown && token != null && !token.canResendAt(now)) {
                return null;
            }
            String rawToken = newRawToken();
            OffsetDateTime expiresAt = now.plus(properties.getTokenTtl());
            OffsetDateTime resendAvailableAt = now.plus(properties.getResendCooldown());
            if (token == null) {
                token = new EmailVerificationToken(user, hash(rawToken), expiresAt, now, resendAvailableAt);
            } else {
                token.refresh(hash(rawToken), expiresAt, now, resendAvailableAt);
            }
            EmailVerificationToken saved = tokens.saveAndFlush(token);
            return new IssuedVerification(saved.getId(), user, rawToken);
        });
    }

    private EmailDeliveryAttemptStatus sendAndRecord(IssuedVerification issued) {
        OffsetDateTime attemptedAt = OffsetDateTime.now(clock);
        try {
            EmailSendResult result = emailSender.sendVerificationEmail(issued.user(), issued.rawToken());
            if (result == null) {
                return recordFailure(
                        issued,
                        EmailDeliveryAttemptStatus.INVALID_RESPONSE,
                        null,
                        "EMPTY_PROVIDER_RESPONSE",
                        "Почтовый провайдер не вернул результат",
                        attemptedAt
                );
            }
            EmailDeliveryAttemptStatus deliveryStatus = result.result()
                    ? EmailDeliveryAttemptStatus.ACCEPTED
                    : EmailDeliveryAttemptStatus.REJECTED;
            recordAttempt(
                    issued,
                    deliveryStatus,
                    result.result(),
                    result.recipientEmail(),
                    result.providerMessageId(),
                    result.httpStatus(),
                    result.result() ? null : "PROVIDER_REJECTED",
                    result.result() ? null : "SMTP.BZ отклонил письмо",
                    attemptedAt
            );
            return deliveryStatus;
        } catch (EmailDeliveryException ex) {
            return recordFailure(
                    issued,
                    ex.getStatus(),
                    ex.getHttpStatus(),
                    ex.getErrorCode(),
                    ex.getMessage(),
                    attemptedAt
            );
        } catch (RuntimeException ex) {
            log.error("Неожиданная ошибка отправки подтверждения: userId={}", issued.user().getId(), ex);
            return recordFailure(
                    issued,
                    EmailDeliveryAttemptStatus.INVALID_RESPONSE,
                    null,
                    "UNEXPECTED_DELIVERY_ERROR",
                    "Неожиданная ошибка почтовой интеграции",
                    attemptedAt
            );
        }
    }

    private EmailDeliveryAttemptStatus recordFailure(
            IssuedVerification issued,
            EmailDeliveryAttemptStatus deliveryStatus,
            Integer httpStatus,
            String errorCode,
            String errorMessage,
            OffsetDateTime attemptedAt
    ) {
        recordAttempt(
                issued,
                deliveryStatus,
                null,
                issued.user().getEmail(),
                null,
                httpStatus,
                errorCode,
                errorMessage,
                attemptedAt
        );
        return deliveryStatus;
    }

    private void recordAttempt(
            IssuedVerification issued,
            EmailDeliveryAttemptStatus deliveryStatus,
            Boolean providerResult,
            String recipientEmail,
            String messageId,
            Integer httpStatus,
            String errorCode,
            String errorMessage,
            OffsetDateTime attemptedAt
    ) {
        Long attemptId = transactions.execute(status -> {
            EmailVerificationToken verification = tokens.findById(issued.verificationId())
                    .orElseThrow(() -> new IllegalStateException("Verification token disappeared"));
            EmailDeliveryAttempt attempt = deliveryAttempts.save(new EmailDeliveryAttempt(
                    verification,
                    deliveryStatus,
                    providerResult,
                    recipientEmail == null ? issued.user().getEmail() : recipientEmail,
                    messageId,
                    httpStatus,
                    errorCode,
                    truncate(errorMessage),
                    attemptedAt,
                    OffsetDateTime.now(clock)
            ));
            return attempt.getId();
        });
        log.info(
                "Сохранён результат отправки подтверждения: attemptId={}, userId={}, status={}, messageId={}",
                attemptId, issued.user().getId(), deliveryStatus, messageId
        );
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM does not support SHA-256", ex);
        }
    }

    private record IssuedVerification(Long verificationId, AppUser user, String rawToken) {
    }
}
