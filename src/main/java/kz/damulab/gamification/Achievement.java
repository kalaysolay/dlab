package kz.damulab.gamification;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "achievements")
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "title_kk", nullable = false)
    private String titleKk;

    @Column(name = "description_ru", nullable = false, length = 500)
    private String descriptionRu;

    @Column(name = "description_kk", nullable = false, length = 500)
    private String descriptionKk;

    @Column(name = "metric_code", nullable = false, length = 64)
    private String metricCode;

    @Column(name = "required_value", nullable = false)
    private int requiredValue;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Achievement() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle(String language) {
        return "kk".equals(language) ? titleKk : titleRu;
    }

    public String getDescription(String language) {
        return "kk".equals(language) ? descriptionKk : descriptionRu;
    }

    public String getMetricCode() {
        return metricCode;
    }

    public int getRequiredValue() {
        return requiredValue;
    }

    public boolean isActive() {
        return active;
    }
}
