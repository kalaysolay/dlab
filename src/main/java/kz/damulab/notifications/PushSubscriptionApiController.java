package kz.damulab.notifications;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для регистрации Web Push подписки браузера.
 *
 * POST /api/push/subscribe — принимает PushSubscription.toJSON() от клиента,
 * делает upsert в device_tokens (dedup по SHA-256 от endpoint).
 *
 * Доступен только аутентифицированному STUDENT (см. SecurityConfig).
 * CSRF отключён для /api/push/** (браузер отправляет fetch с JSON).
 */
@RestController
@RequestMapping("/api/push")
public class PushSubscriptionApiController {

    private final DeviceTokenRepository deviceTokens;
    private final AppUserRepository users;
    private final ObjectMapper objectMapper;

    public PushSubscriptionApiController(DeviceTokenRepository deviceTokens,
                                         AppUserRepository users,
                                         ObjectMapper objectMapper) {
        this.deviceTokens = deviceTokens;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    /**
     * Сохраняет или обновляет Web Push подписку для текущего пользователя.
     * Идемпотентен: повторная подписка с тем же endpoint обновляет ключи.
     *
     * @param request  подписка из PushManager.subscribe().toJSON()
     * @param auth     Spring Security аутентификация (STUDENT)
     */
    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void subscribe(@RequestBody @Valid PushSubscriptionRequest request,
                          Authentication auth) throws JsonProcessingException, NoSuchAlgorithmException {

        AppUser user = users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + auth.getName()));

        // Сериализуем подписку в стандартный JSON для хранения и последующей отправки
        String subscriptionJson = objectMapper.writeValueAsString(Map.of(
                "endpoint", request.endpoint(),
                "keys", Map.of(
                        "p256dh", request.keys().p256dh(),
                        "auth", request.keys().auth()
                )
        ));

        // SHA-256(endpoint) — ключ дедупликации (уникальный per-browser-profile)
        String tokenHash = sha256Hex(request.endpoint());

        String timezone = request.timezone();

        // Upsert: если подписка уже есть — обновляем ключи (могут обновиться при повторной регистрации SW)
        deviceTokens.findByProviderAndTokenHash("webpush", tokenHash)
                .ifPresentOrElse(
                        token -> token.updateSubscription(subscriptionJson, timezone),
                        () -> deviceTokens.save(
                                new DeviceToken(user, "webpush", "browser", tokenHash, subscriptionJson, timezone)
                        )
                );
    }

    private static String sha256Hex(String input) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
