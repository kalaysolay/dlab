package kz.damulab.parentlink;

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
import kz.damulab.auth.EmailDeliveryAttemptStatus;
import kz.damulab.auth.EmailDeliveryException;
import kz.damulab.auth.EmailSendResult;
import kz.damulab.users.ParentProfile;
import kz.damulab.users.ParentProfileRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

/**
 * Выпускает и погашает email-приглашения. Токен содержит 256 случайных бит, но не содержит
 * идентификаторы: связь с конкретными parent/student хранится только в серверной записи.
 */
@Service
public class ParentLinkInvitationService {

    private static final Logger log = LoggerFactory.getLogger(ParentLinkInvitationService.class);
    private static final int TOKEN_BYTES = 32;

    private final ParentProfileRepository parentProfiles;
    private final StudentProfileRepository studentProfiles;
    private final ParentStudentLinkRepository links;
    private final ParentLinkInvitationRepository invitations;
    private final ParentLinkDeliveryAttemptRepository deliveryAttempts;
    private final ParentLinkInvitationEmailSender emailSender;
    private final ParentLinkInvitationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SecureRandom secureRandom = new SecureRandom();

    public ParentLinkInvitationService(
            ParentProfileRepository parentProfiles,
            StudentProfileRepository studentProfiles,
            ParentStudentLinkRepository links,
            ParentLinkInvitationRepository invitations,
            ParentLinkDeliveryAttemptRepository deliveryAttempts,
            ParentLinkInvitationEmailSender emailSender,
            ParentLinkInvitationProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.parentProfiles = parentProfiles;
        this.studentProfiles = studentProfiles;
        this.links = links;
        this.invitations = invitations;
        this.deliveryAttempts = deliveryAttempts;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Принимает запрос одинаково для существующего и неизвестного адреса. Внешний результат
     * метода не должен использоваться для различения этих случаев.
     */
    public void request(String parentEmail, String rawStudentEmail) {
        IssuedInvitation issued = transactions.execute(status -> issue(parentEmail, rawStudentEmail));
        if (issued != null) {
            sendAndRecord(issued);
        }
    }

    /** Проверяет токен и совпадение вошедшего аккаунта, не возвращая персональные данные. */
    public ParentLinkInvitationViewState inspect(String rawToken, String authenticatedEmail) {
        if (!isValidRawToken(rawToken)) {
            return ParentLinkInvitationViewState.INVALID;
        }
        return transactions.execute(status -> {
            ParentLinkInvitation invitation = invitations.findByTokenHashForUpdate(hash(rawToken)).orElse(null);
            if (invitation == null || !invitation.isPending()) {
                return ParentLinkInvitationViewState.INVALID;
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (invitation.isExpiredAt(now)) {
                invitation.markExpired(now);
                return ParentLinkInvitationViewState.INVALID;
            }
            if (authenticatedEmail == null) {
                return ParentLinkInvitationViewState.LOGIN_REQUIRED;
            }
            StudentProfile authenticatedStudent = studentProfiles
                    .findByUserEmailIgnoreCase(authenticatedEmail).orElse(null);
            if (authenticatedStudent == null
                    || !authenticatedStudent.getId().equals(invitation.getStudentProfile().getId())) {
                return ParentLinkInvitationViewState.WRONG_ACCOUNT;
            }
            return ParentLinkInvitationViewState.READY;
        });
    }

    /**
     * Создаёт связь только когда авторизованный student совпадает с student_profile_id приглашения.
     * Повторное или истёкшее приглашение возвращает доменную ошибку без раскрытия деталей.
     */
    public void confirm(String studentEmail, String rawToken) {
        if (!isValidRawToken(rawToken)) {
            throw new ParentLinkException("invitation_not_available");
        }
        transactions.executeWithoutResult(status -> {
            ParentLinkInvitation invitation = invitations.findByTokenHashForUpdate(hash(rawToken))
                    .orElseThrow(() -> new ParentLinkException("invitation_not_available"));
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (!invitation.isPending() || invitation.isExpiredAt(now)) {
                if (invitation.isPending()) {
                    invitation.markExpired(now);
                }
                throw new ParentLinkException("invitation_not_available");
            }
            StudentProfile authenticatedStudent = studentProfiles.findByUserEmailIgnoreCase(studentEmail)
                    .orElseThrow(() -> new ParentLinkException("invitation_student_mismatch"));
            StudentProfile invitedStudent = invitation.getStudentProfile();
            if (!authenticatedStudent.getId().equals(invitedStudent.getId())) {
                throw new ParentLinkException("invitation_student_mismatch");
            }
            ParentProfile parent = invitation.getParentProfile();
            if (!links.existsByParentProfileAndStudentProfile(parent, invitedStudent)) {
                links.save(new ParentStudentLink(parent, invitedStudent));
            }
            invitation.confirm(now);
        });
    }

    private IssuedInvitation issue(String parentEmail, String rawStudentEmail) {
        ParentProfile parent = parentProfiles.findByUserEmailIgnoreCase(parentEmail)
                .orElseThrow(() -> new ParentLinkException("parent_not_found"));
        String studentEmail = normalizeEmail(rawStudentEmail);
        StudentProfile student = studentProfiles.findByUserEmailIgnoreCase(studentEmail).orElse(null);
        if (student == null || links.existsByParentProfileAndStudentProfile(parent, student)) {
            return null;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        ParentLinkInvitation invitation = invitations.findPairForUpdate(parent, student).orElse(null);
        if (invitation != null && invitation.isPending() && !invitation.canResendAt(now)) {
            return null;
        }
        String rawToken = newRawToken();
        OffsetDateTime expiresAt = now.plus(properties.getTokenTtl());
        OffsetDateTime resendAt = now.plus(properties.getResendCooldown());
        if (invitation == null) {
            invitation = new ParentLinkInvitation(parent, student, hash(rawToken), now, expiresAt, resendAt);
        } else {
            invitation.refresh(hash(rawToken), now, expiresAt, resendAt);
        }
        ParentLinkInvitation saved = invitations.saveAndFlush(invitation);
        return new IssuedInvitation(saved.getId(), student.getUser().getEmail(), rawToken);
    }

    private void sendAndRecord(IssuedInvitation issued) {
        OffsetDateTime attemptedAt = OffsetDateTime.now(clock);
        EmailDeliveryAttemptStatus deliveryStatus;
        String messageId = null;
        Integer httpStatus = null;
        String errorCode = null;
        try {
            EmailSendResult result = emailSender.sendInvitation(issued.studentEmail(), issued.rawToken());
            if (result == null) {
                deliveryStatus = EmailDeliveryAttemptStatus.INVALID_RESPONSE;
                errorCode = "EMPTY_PROVIDER_RESPONSE";
            } else {
                deliveryStatus = result.result()
                        ? EmailDeliveryAttemptStatus.ACCEPTED
                        : EmailDeliveryAttemptStatus.REJECTED;
                messageId = result.providerMessageId();
                httpStatus = result.httpStatus();
                errorCode = result.result() ? null : "PROVIDER_REJECTED";
            }
        } catch (EmailDeliveryException ex) {
            deliveryStatus = ex.getStatus();
            httpStatus = ex.getHttpStatus();
            errorCode = ex.getErrorCode();
        } catch (RuntimeException ex) {
            // Не логируем адрес и токен: invitationId достаточно для сопоставления с аудитом БД.
            log.error("Неожиданная ошибка отправки приглашения: invitationId={}", issued.invitationId(), ex);
            deliveryStatus = EmailDeliveryAttemptStatus.INVALID_RESPONSE;
            errorCode = "UNEXPECTED_DELIVERY_ERROR";
        }
        EmailDeliveryAttemptStatus finalStatus = deliveryStatus;
        String finalMessageId = messageId;
        Integer finalHttpStatus = httpStatus;
        String finalErrorCode = errorCode;
        transactions.executeWithoutResult(status -> {
            ParentLinkInvitation invitation = invitations.findById(issued.invitationId())
                    .orElseThrow(() -> new IllegalStateException("Parent link invitation disappeared"));
            deliveryAttempts.save(new ParentLinkDeliveryAttempt(
                    invitation, finalStatus, issued.studentEmail(), finalMessageId,
                    finalHttpStatus, finalErrorCode, attemptedAt, OffsetDateTime.now(clock)
            ));
        });
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isValidRawToken(String rawToken) {
        return rawToken != null && rawToken.matches("[A-Za-z0-9_-]{43}");
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

    private record IssuedInvitation(Long invitationId, String studentEmail, String rawToken) {
    }
}
