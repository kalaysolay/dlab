package kz.damulab.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "grades")
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade_no", nullable = false, unique = true)
    private Integer gradeNo;

    @Column(name = "title_ru", nullable = false, length = 64)
    private String titleRu;

    @Column(name = "title_kk", nullable = false, length = 64)
    private String titleKk;

    protected Grade() {
    }

    public Long getId() {
        return id;
    }

    public Integer getGradeNo() {
        return gradeNo;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getTitleKk() {
        return titleKk;
    }
}
