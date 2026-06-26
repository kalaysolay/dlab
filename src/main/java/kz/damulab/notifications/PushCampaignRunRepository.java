package kz.damulab.notifications;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushCampaignRunRepository extends JpaRepository<PushCampaignRun, Long> {

    /**
     * Последние запуски кампании — для отображения статистики в админке.
     * Упорядочены по убыванию времени (самые свежие первые).
     */
    List<PushCampaignRun> findTop10ByCampaignOrderByTriggeredAtDesc(PushCampaign campaign);

    /** Все запуски данной кампании — для подробной статистики. */
    List<PushCampaignRun> findByCampaignOrderByTriggeredAtDesc(PushCampaign campaign);
}
