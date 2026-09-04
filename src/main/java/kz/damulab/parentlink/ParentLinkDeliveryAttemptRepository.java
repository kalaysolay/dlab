package kz.damulab.parentlink;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentLinkDeliveryAttemptRepository extends JpaRepository<ParentLinkDeliveryAttempt, Long> {

    List<ParentLinkDeliveryAttempt> findAllByInvitationIdOrderByAttemptedAtAsc(Long invitationId);
}
