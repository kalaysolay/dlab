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
@Table(name = "topics")
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Topic parentTopic;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "title_kk", nullable = false)
    private String titleKk;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(nullable = false)
    private boolean imported;

    @Column(name = "import_note", length = 512)
    private String importNote;

    @Column(name = "imported_at")
    private OffsetDateTime importedAt;

    protected Topic() {
    }

    public Topic(Subject subject, Grade grade, Topic parentTopic, String code, String titleRu, String titleKk) {
        this.subject = subject;
        this.grade = grade;
        this.parentTopic = parentTopic;
        this.code = code;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public void update(Subject subject, Grade grade, Topic parentTopic, String code, String titleRu, String titleKk) {
        this.subject = subject;
        this.grade = grade;
        this.parentTopic = parentTopic;
        this.code = code;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
    }

    public void markImported(String importNote, OffsetDateTime importedAt) {
        this.imported = true;
        this.importNote = importNote;
        this.importedAt = importedAt;
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public Grade getGrade() {
        return grade;
    }

    public Topic getParentTopic() {
        return parentTopic;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isImported() {
        return imported;
    }

    public String getImportNote() {
        return importNote;
    }

    public OffsetDateTime getImportedAt() {
        return importedAt;
    }
}
