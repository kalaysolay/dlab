package kz.damulab.notifications;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeliveryLogRepository extends JpaRepository<PushDeliveryLog, Long> {

    List<PushDeliveryLog> findByPushNotificationIdOrderByAttemptedAtDesc(Long pushNotificationId);
}
