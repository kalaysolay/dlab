package kz.damulab.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, Long> {

    List<EmailDeliveryAttempt> findAllByVerificationIdOrderByAttemptedAtAsc(Long verificationId);
}
