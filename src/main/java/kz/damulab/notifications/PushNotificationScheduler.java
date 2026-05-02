package kz.damulab.notifications;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationScheduler {

    private final PushNotificationService pushNotifications;

    public PushNotificationScheduler(PushNotificationService pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    @Scheduled(fixedDelayString = "${damulab.push.worker-delay-ms:60000}")
    void processDueNotifications() {
        pushNotifications.processDueNotifications();
    }
}
