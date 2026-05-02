package kz.damulab.lectures;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import kz.damulab.users.AppUser;

@Entity
@Table(name = "lectures")
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private LectureVersion currentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LectureStatus status = LectureStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Lecture() {
    }

    public Lecture(LectureStatus status, AppUser createdBy) {
        this.status = status;
        this.createdBy = createdBy;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public void setCurrentVersion(LectureVersion currentVersion) {
        this.currentVersion = currentVersion;
    }

    public void changeStatus(LectureStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public LectureVersion getCurrentVersion() {
        return currentVersion;
    }

    public LectureStatus getStatus() {
        return status;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
