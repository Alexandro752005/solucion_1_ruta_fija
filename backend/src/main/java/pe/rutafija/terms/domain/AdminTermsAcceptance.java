package pe.rutafija.terms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_terms_acceptance")
public class AdminTermsAcceptance {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false, updatable = false)
    private AppUser adminUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private AdminTermsDocument document;

    @Column(name = "document_version", nullable = false, length = 40, updatable = false)
    private String documentVersion;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    protected AdminTermsAcceptance() {
    }

    private AdminTermsAcceptance(
            AppUser adminUser,
            Organization organization,
            AdminTermsDocument document,
            Instant acceptedAt
    ) {
        this.id = UUID.randomUUID();
        this.adminUser = Objects.requireNonNull(adminUser);
        this.organization = Objects.requireNonNull(organization);
        this.document = Objects.requireNonNull(document);
        this.documentVersion = document.getVersion();
        this.contentSha256 = document.getContentSha256();
        this.acceptedAt = Objects.requireNonNull(acceptedAt);
    }

    public static AdminTermsAcceptance record(
            AppUser adminUser,
            Organization organization,
            AdminTermsDocument document,
            Instant acceptedAt
    ) {
        if (adminUser.getRole() != UserRole.ADMIN
                || adminUser.getOrganizationId() == null
                || !organization.getId().equals(adminUser.getOrganizationId())) {
            throw new IllegalArgumentException("The acceptance organization must match the ADMIN account");
        }
        return new AdminTermsAcceptance(adminUser, organization, document, acceptedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminUserId() {
        return adminUser.getId();
    }

    public UUID getOrganizationId() {
        return organization.getId();
    }

    public UUID getDocumentId() {
        return document.getId();
    }

    public String getDocumentVersion() {
        return documentVersion;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public boolean isSynthetic() {
        return document.isSynthetic();
    }
}
