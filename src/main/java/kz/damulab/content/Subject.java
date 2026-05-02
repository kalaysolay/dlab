package kz.damulab.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "title_kk", nullable = false)
    private String titleKk;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Subject() {
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
}
