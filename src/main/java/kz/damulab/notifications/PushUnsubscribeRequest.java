package kz.damulab.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/** Минимальный payload для отключения конкретной браузерной подписки. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PushUnsubscribeRequest(@NotBlank String endpoint) {
}
