package kz.damulab.notifications;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushCampaignRepository extends JpaRepository<PushCampaign, Long> {

    /** Все включённые кампании — проверяются планировщиком каждую минуту. */
    List<PushCampaign> findByEnabledTrueOrderByCreatedAtDesc();

    List<PushCampaign> findAllByOrderByCreatedAtDesc();
}
