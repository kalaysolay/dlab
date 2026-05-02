package kz.damulab.questions;

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
@Table(name = "question_import_errors")
public class QuestionImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private QuestionImportJob job;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "error_code", nullable = false, length = 128)
    private String errorCode;

    @Column(name = "message", nullable = false, length = 512)
    private String message;

    protected QuestionImportError() {
    }

    public QuestionImportError(QuestionImportJob job, int rowNo, String errorCode, String message) {
        this.job = job;
        this.rowNo = rowNo;
        this.errorCode = errorCode;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public QuestionImportJob getJob() {
        return job;
    }

    public int getRowNo() {
        return rowNo;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
