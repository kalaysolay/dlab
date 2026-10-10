package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Неизменяемая запись аудита одного редакционного перехода материала или набора. */
@Entity
@Table(name = "quiz_content_editorial_events")
public class QuizContentEditorialEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "content_type", nullable = false, length = 48) private QuizContentType contentType;
    @Column(name = "content_id", nullable = false) private Long contentId;
    @Column(name = "version_id") private Long versionId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", nullable = false, length = 32) private EditorialStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 32) private EditorialStatus toStatus;
    @Column(name = "actor_id") private Long actorId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    protected QuizContentEditorialEvent() {}

    /** Создаёт запись перехода; журнал не предоставляет методов изменения. */
    public QuizContentEditorialEvent(QuizContentType contentType, Long contentId, Long versionId,
                                     EditorialStatus fromStatus, EditorialStatus toStatus, Long actorId) {
        this.contentType = contentType; this.contentId = contentId; this.versionId = versionId;
        this.fromStatus = fromStatus; this.toStatus = toStatus; this.actorId = actorId;
    }

    public Long getId() { return id; }
    public QuizContentType getContentType() { return contentType; }
    public Long getContentId() { return contentId; }
    public Long getVersionId() { return versionId; }
    public EditorialStatus getFromStatus() { return fromStatus; }
    public EditorialStatus getToStatus() { return toStatus; }
    public Long getActorId() { return actorId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
