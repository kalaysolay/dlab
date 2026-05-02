package kz.damulab.notifications;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushNotificationRepository extends JpaRepository<PushNotification, Long> {

    List<PushNotification> findAllByOrderByCreatedAtDesc();

    List<PushNotification> findByStatusOrderByCreatedAtDesc(PushNotificationStatus status);

    List<PushNotification> findByStatusAndScheduledAtUtcLessThanEqualOrderByScheduledAtUtcAsc(
            PushNotificationStatus status,
            OffsetDateTime scheduledAtUtc
    );
}
