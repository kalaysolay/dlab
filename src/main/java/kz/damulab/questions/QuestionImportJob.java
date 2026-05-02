package kz.damulab.questions;

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
@Table(name = "question_import_jobs")
public class QuestionImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuestionImportJobStatus status = QuestionImportJobStatus.FAILED;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "JSON";

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "source_payload")
    private String sourcePayload;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected QuestionImportJob() {
    }

    public QuestionImportJob(String sourceType, int totalRows) {
        this(sourceType, totalRows, null, null);
    }

    public QuestionImportJob(String sourceType, int totalRows, String originalFilename, String sourcePayload) {
        this.sourceType = sourceType;
        this.totalRows = totalRows;
        this.originalFilename = originalFilename;
        this.sourcePayload = sourcePayload;
    }

    public void complete(int importedRows, int errorRows) {
        this.importedRows = importedRows;
        this.errorRows = errorRows;
        if (importedRows > 0 && errorRows == 0) {
            this.status = QuestionImportJobStatus.COMPLETED;
        } else if (importedRows > 0) {
            this.status = QuestionImportJobStatus.PARTIAL;
        } else {
            this.status = QuestionImportJobStatus.FAILED;
        }
    }

    public Long getId() {
        return id;
    }

    public QuestionImportJobStatus getStatus() {
        return status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getSourcePayload() {
        return sourcePayload;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
