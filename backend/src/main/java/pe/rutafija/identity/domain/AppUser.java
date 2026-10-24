package pe.rutafija.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "session_version", nullable = false)
    private long sessionVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    private AppUser(
            Organization organization,
            String email,
            String passwordHash,
            String fullName,
            UserRole role
    ) {
        if (role == UserRole.SUPER_ADMIN && organization != null) {
            throw new IllegalArgumentException("SUPER_ADMIN must not belong to an organization");
        }
        if (role != UserRole.SUPER_ADMIN && organization == null) {
            throw new IllegalArgumentException("An organization is required for this role");
        }
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.fullName = requiredText(fullName, "El nombre completo es obligatorio");
        this.role = Objects.requireNonNull(role);
        this.active = true;
    }

    public static AppUser organizationUser(
            Organization organization,
            String email,
            String passwordHash,
            String fullName,
            UserRole role
    ) {
        return new AppUser(organization, email, passwordHash, fullName, role);
    }

    public static AppUser superAdmin(String email, String passwordHash, String fullName) {
        return new AppUser(null, email, passwordHash, fullName, UserRole.SUPER_ADMIN);
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
    }

    public void recordSuccessfulLogin(Instant occurredAt) {
        this.lastLoginAt = Objects.requireNonNull(occurredAt);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void verifyEmail(Instant verifiedAt) {
        this.emailVerifiedAt = Objects.requireNonNull(verifiedAt);
    }

    public void completeSuperAdminRecovery(String newEmail, String newPasswordHash, Instant recoveredAt) {
        if (role != UserRole.SUPER_ADMIN || !active) {
            throw new IllegalStateException("Only an active SUPER_ADMIN can complete this recovery");
        }
        this.email = normalizeEmail(newEmail);
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.emailVerifiedAt = Objects.requireNonNull(recoveredAt);
        this.sessionVersion = Math.addExact(this.sessionVersion, 1);
    }

    public void completeConductorEmailRecovery(String newEmail, Instant verifiedAt) {
        if (role != UserRole.CONDUCTOR || !active || organization == null) {
            throw new IllegalStateException("Only an active organization CONDUCTOR can recover an email");
        }
        this.email = normalizeEmail(newEmail);
        this.emailVerifiedAt = Objects.requireNonNull(verifiedAt);
        this.sessionVersion = Math.addExact(this.sessionVersion, 1);
    }

    public void completeConductorPasswordReset(String newEmail, String newPasswordHash, Instant resetAt) {
        if (role != UserRole.CONDUCTOR || !active || organization == null) {
            throw new IllegalStateException("Only an active organization CONDUCTOR can reset a password");
        }
        this.email = normalizeEmail(newEmail);
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.emailVerifiedAt = Objects.requireNonNull(resetAt);
        this.sessionVersion = Math.addExact(this.sessionVersion, 1);
    }

    public void updateDetails(String email, String fullName, String phone, UserRole role) {
        UserRole requestedRole = Objects.requireNonNull(role);
        if (requestedRole == UserRole.SUPER_ADMIN || organization == null) {
            throw new IllegalArgumentException("Un usuario de organización no puede convertirse en SUPER_ADMIN");
        }
        if (requestedRole != this.role) {
            throw new IllegalArgumentException("El rol de una identidad creada es inmutable durante el piloto");
        }
        String normalizedEmail = normalizeEmail(email);
        if (!this.email.equals(normalizedEmail)) {
            this.emailVerifiedAt = null;
        }
        this.email = normalizedEmail;
        this.fullName = requiredText(fullName, "El nombre completo es obligatorio");
        this.phone = optionalText(phone);
        this.role = requestedRole;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UUID getOrganizationId() {
        return organization == null ? null : organization.getId();
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }

    private static String requiredText(String value, String message) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
