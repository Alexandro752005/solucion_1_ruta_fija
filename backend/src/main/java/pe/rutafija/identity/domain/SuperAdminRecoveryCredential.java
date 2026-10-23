package pe.rutafija.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "super_admin_recovery_credential")
public class SuperAdminRecoveryCredential {

    private static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "installation_id", nullable = false, unique = true)
    private UUID installationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "public_key_x509", nullable = false, length = 256)
    private String publicKeyX509;

    @Column(name = "emergency_code_hash", nullable = false, length = 100)
    private String emergencyCodeHash;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SuperAdminRecoveryCredential() {
    }

    private SuperAdminRecoveryCredential(
            AppUser user,
            UUID installationId,
            String publicKeyX509,
            String emergencyCodeHash,
            Instant enrolledAt
    ) {
        if (user.getRole() != UserRole.SUPER_ADMIN || !user.isActive() || !user.isEmailVerified()) {
            throw new IllegalArgumentException("An active, verified SUPER_ADMIN is required for recovery enrollment");
        }
        this.id = SINGLETON_ID;
        this.user = Objects.requireNonNull(user);
        this.installationId = Objects.requireNonNull(installationId);
        this.publicKeyX509 = Objects.requireNonNull(publicKeyX509);
        this.emergencyCodeHash = Objects.requireNonNull(emergencyCodeHash);
        this.enrolledAt = Objects.requireNonNull(enrolledAt);
        this.updatedAt = enrolledAt;
    }

    public static SuperAdminRecoveryCredential enroll(
            AppUser user,
            UUID installationId,
            String publicKeyX509,
            String emergencyCodeHash,
            Instant enrolledAt
    ) {
        return new SuperAdminRecoveryCredential(
                user,
                installationId,
                publicKeyX509,
                emergencyCodeHash,
                enrolledAt
        );
    }

    public void rotateEmergencyCode(String emergencyCodeHash, Instant now) {
        this.emergencyCodeHash = Objects.requireNonNull(emergencyCodeHash);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public UUID getInstallationId() {
        return installationId;
    }

    public AppUser getUser() {
        return user;
    }

    public String getPublicKeyX509() {
        return publicKeyX509;
    }

    public String getEmergencyCodeHash() {
        return emergencyCodeHash;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }
}
