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
import kz.damulab.users.StudentProfile;

@Entity
@Table(name = "link_codes")
public class LinkCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected LinkCode() {
    }

    public LinkCode(StudentProfile studentProfile, String code, OffsetDateTime expiresAt) {
        this.studentProfile = studentProfile;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public String getCode() {
        return code;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public boolean isUsable(OffsetDateTime now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(OffsetDateTime now) {
        this.consumedAt = now;
    }
}
