package kz.damulab.passkeys;

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
import kz.damulab.users.AppUser;

@Entity
@Table(name = "passkey_credentials")
public class PasskeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "credential_id", nullable = false, unique = true, length = 512)
    private String credentialId;

    @Column(name = "public_key_cose", nullable = false)
    private byte[] publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(nullable = false)
    private boolean discoverable;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backed_up", nullable = false)
    private boolean backedUp;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    protected PasskeyCredential() {
    }

    public PasskeyCredential(
            AppUser user,
            String credentialId,
            byte[] publicKeyCose,
            long signatureCount,
            boolean discoverable,
            boolean backupEligible,
            boolean backedUp
    ) {
        this.user = user;
        this.credentialId = credentialId;
        this.publicKeyCose = publicKeyCose;
        this.signatureCount = signatureCount;
        this.discoverable = discoverable;
        this.backupEligible = backupEligible;
        this.backedUp = backedUp;
    }

    public AppUser getUser() {
        return user;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public byte[] getPublicKeyCose() {
        return publicKeyCose;
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public void markUsed(long signatureCount, boolean backedUp) {
        this.signatureCount = signatureCount;
        this.backedUp = backedUp;
        this.lastUsedAt = OffsetDateTime.now();
    }
}
