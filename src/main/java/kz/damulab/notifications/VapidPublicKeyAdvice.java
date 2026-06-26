package kz.damulab.notifications;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Добавляет VAPID public key в модель каждой Thymeleaf-страницы.
 *
 * Шаблоны используют ${vapidPublicKey} для рендеринга
 * &lt;meta name="vapid-public-key"&gt; (в pwa-head.html).
 * pwa.js читает этот тег при подписке на push-уведомления.
 *
 * При пустом ключе (stub-режим, local-профиль) тег не рендерится,
 * push-подписка недоступна — это штатное поведение.
 */
@ControllerAdvice
public class VapidPublicKeyAdvice {

    private final VapidProperties vapid;

    public VapidPublicKeyAdvice(VapidProperties vapid) {
        this.vapid = vapid;
    }

    @ModelAttribute("vapidPublicKey")
    public String vapidPublicKey() {
        return vapid.getVapidPublicKey();
    }
}
