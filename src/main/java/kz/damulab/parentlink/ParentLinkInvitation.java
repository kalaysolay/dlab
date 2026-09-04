package kz.damulab.parentlink;

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
import jakarta.persistence.UniqueConstraint;
import kz.damulab.users.ParentProfile;
import kz.damulab.users.StudentProfile;

/**
 * Приглашение связывает секретный токен с одной точной парой parent/student. Сырой токен
 * никогда не сохраняется: по ссылке ищется только его SHA-256 хеш.
 */
@Entity
@Table(
        name = "parent_link_invitations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_parent_link_invitation_pair",
                columnNames = {"parent_profile_id", "student_profile_id"}
        )
)
public class ParentLinkInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_profile_id", nullable = false)
    private ParentProfile parentProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParentLinkInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private OffsetDateTime resendAvailableAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    protected ParentLinkInvitation() {
    }

    public ParentLinkInvitation(
            ParentProfile parentProfile,
            StudentProfile studentProfile,
            String tokenHash,
            OffsetDateTime now,
            OffsetDateTime expiresAt,
            OffsetDateTime resendAvailableAt
    ) {
        this.parentProfile = parentProfile;
        this.studentProfile = studentProfile;
        refresh(tokenHash, now, expiresAt, resendAvailableAt);
        this.createdAt = now;
    }

    /** Заменяет старую ссылку новой, сохраняя одну строку на пару parent/student. */
    public void refresh(
            String tokenHash,
            OffsetDateTime now,
            OffsetDateTime expiresAt,
            OffsetDateTime resendAvailableAt
    ) {
        this.tokenHash = tokenHash;
        this.status = ParentLinkInvitationStatus.PENDING;
        this.expiresAt = expiresAt;
        this.resendAvailableAt = resendAvailableAt;
        this.updatedAt = now;
        this.confirmedAt = null;
    }

    public boolean canResendAt(OffsetDateTime now) {
        return !now.isBefore(resendAvailableAt);
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isPending() {
        return status == ParentLinkInvitationStatus.PENDING;
    }

    public void markExpired(OffsetDateTime now) {
        status = ParentLinkInvitationStatus.EXPIRED;
        updatedAt = now;
    }

    public void confirm(OffsetDateTime now) {
        status = ParentLinkInvitationStatus.CONFIRMED;
        confirmedAt = now;
        updatedAt = now;
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

    public ParentLinkInvitationStatus getStatus() {
        return status;
    }
}
