package kz.damulab.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "atomic_skills")
public class AtomicSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "title_kk", nullable = false)
    private String titleKk;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected AtomicSkill() {
    }

    public AtomicSkill(Topic topic, String code, String titleRu, String titleKk, boolean active) {
        this.topic = topic;
        this.code = code;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
        this.active = active;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public void update(Topic topic, String code, String titleRu, String titleKk, boolean active) {
        this.topic = topic;
        this.code = code;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Topic getTopic() {
        return topic;
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

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
