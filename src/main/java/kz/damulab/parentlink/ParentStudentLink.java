package kz.damulab.parentlink;

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
import jakarta.persistence.UniqueConstraint;
import kz.damulab.users.ParentProfile;
import kz.damulab.users.StudentProfile;

@Entity
@Table(
        name = "parent_student_links",
        uniqueConstraints = @UniqueConstraint(name = "uq_parent_student_link", columnNames = {"parent_profile_id", "student_profile_id"})
)
public class ParentStudentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_profile_id", nullable = false)
    private ParentProfile parentProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ParentStudentLink() {
    }

    public ParentStudentLink(ParentProfile parentProfile, StudentProfile studentProfile) {
        this.parentProfile = parentProfile;
        this.studentProfile = studentProfile;
    }

    public Long getId() {
        return id;
    }

    public ParentProfile getParentProfile() {
        return parentProfile;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
