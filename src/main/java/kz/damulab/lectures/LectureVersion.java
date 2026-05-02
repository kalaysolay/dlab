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
import jakarta.persistence.Table;

import kz.damulab.content.Topic;

@Entity
@Table(name = "lecture_versions")
public class LectureVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(name = "title_ru")
    private String titleRu;

    @Column(name = "title_kk")
    private String titleKk;

    @Column(name = "content_ru_html")
    private String contentRuHtml;

    @Column(name = "content_kk_html")
    private String contentKkHtml;

    @Column(length = 512)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_mode", nullable = false, length = 32)
    private LectureControlMode controlMode = LectureControlMode.NONE;

    @Column(name = "checkpoint_count_requested", nullable = false)
    private int checkpointCountRequested;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected LectureVersion() {
    }

    public LectureVersion(
            Lecture lecture,
            int versionNo,
            Topic topic,
            String titleRu,
            String titleKk,
            String contentRuHtml,
            String contentKkHtml,
            String source,
            LectureControlMode controlMode,
            int checkpointCountRequested
    ) {
        this.lecture = lecture;
        this.versionNo = versionNo;
        this.topic = topic;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
        this.contentRuHtml = contentRuHtml;
        this.contentKkHtml = contentKkHtml;
        this.source = source;
        this.controlMode = controlMode;
        this.checkpointCountRequested = checkpointCountRequested;
    }

    public void replaceContent(LectureVersion replacement) {
        this.topic = replacement.topic;
        this.titleRu = replacement.titleRu;
        this.titleKk = replacement.titleKk;
        this.contentRuHtml = replacement.contentRuHtml;
        this.contentKkHtml = replacement.contentKkHtml;
        this.source = replacement.source;
        this.controlMode = replacement.controlMode;
        this.checkpointCountRequested = replacement.checkpointCountRequested;
        this.publishedAt = null;
    }

    public void markPublished() {
        this.publishedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public Topic getTopic() {
        return topic;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getTitleKk() {
        return titleKk;
    }

    public String getContentRuHtml() {
        return contentRuHtml;
    }

    public String getContentKkHtml() {
        return contentKkHtml;
    }

    public String getSource() {
        return source;
    }

    public LectureControlMode getControlMode() {
        return controlMode;
    }

    public int getCheckpointCountRequested() {
        return checkpointCountRequested;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
