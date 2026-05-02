package kz.damulab.testing;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_templates")
public class TestTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "title_kk", nullable = false)
    private String titleKk;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 32)
    private TestType testType;

    @Column(name = "default_question_count", nullable = false)
    private int defaultQuestionCount;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected TestTemplate() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getTitleKk() {
        return titleKk;
    }

    public TestType getTestType() {
        return testType;
    }

    public int getDefaultQuestionCount() {
        return defaultQuestionCount;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public boolean isActive() {
        return active;
    }
}
