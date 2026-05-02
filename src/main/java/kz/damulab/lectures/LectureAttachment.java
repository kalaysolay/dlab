package kz.damulab.lectures;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lecture_attachments")
public class LectureAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_version_id", nullable = false)
    private LectureVersion lectureVersion;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "media_type", nullable = false, length = 64)
    private String mediaType;

    @Column(name = "storage_key", length = 255)
    private String storageKey;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "file_content_type", length = 127)
    private String fileContentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected LectureAttachment() {
    }

    public LectureAttachment(LectureVersion lectureVersion, String title, String url, String mediaType, int sortOrder) {
        this(lectureVersion, title, url, mediaType, null, null, null, null, sortOrder);
    }

    public LectureAttachment(
            LectureVersion lectureVersion,
            String title,
            String url,
            String mediaType,
            String storageKey,
            String originalFileName,
            String fileContentType,
            Long fileSizeBytes,
            int sortOrder
    ) {
        this.lectureVersion = lectureVersion;
        this.title = title;
        this.url = url;
        this.mediaType = mediaType;
        this.storageKey = storageKey;
        this.originalFileName = originalFileName;
        this.fileContentType = fileContentType;
        this.fileSizeBytes = fileSizeBytes;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public LectureVersion getLectureVersion() {
        return lectureVersion;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getMediaType() {
        return mediaType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getFileContentType() {
        return fileContentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public boolean isStoredFile() {
        return storageKey != null && !storageKey.isBlank();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
