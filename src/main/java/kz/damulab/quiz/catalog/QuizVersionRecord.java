package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Номер редакции и аудит проверки. Неизменность опубликованных редакций обеспечивается публикационным сервисом. */
@MappedSuperclass
public abstract class QuizVersionRecord extends QuizEditorialRecord {
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(name = "reviewed_by")
    private Long reviewedBy;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
    protected QuizVersionRecord() {}
    protected QuizVersionRecord(int versionNo) { this.versionNo = versionNo; }
    /** Возвращает номер редакции внутри владельца. */
    public int getVersionNo() { return versionNo; }
    /** Возвращает проверяющего; null означает отсутствие зафиксированной проверки. */
    public Long getReviewedBy() { return reviewedBy; }
    /** Возвращает время проверки. */
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    /** Фиксирует аудит проверки без автоматического перевода в публикацию. */
    public void recordReview(Long userId, OffsetDateTime at) { reviewedBy = userId; reviewedAt = at; }
    /** Возвращает время публикации; оно не равно времени создания. */
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    /** Фиксирует публикационное время после валидации в сервисе; сам метод не валидирует содержимое. */
    public void setPublishedAt(OffsetDateTime value) { publishedAt = value; }
}
