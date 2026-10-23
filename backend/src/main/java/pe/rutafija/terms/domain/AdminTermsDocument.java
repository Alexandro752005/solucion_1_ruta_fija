package pe.rutafija.terms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_terms_document")
public class AdminTermsDocument {

    @Id
    private UUID id;

    @Column(name = "document_key", nullable = false, length = 64, updatable = false)
    private String documentKey;

    @Column(nullable = false, length = 40, updatable = false)
    private String version;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String content;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Column(nullable = false, updatable = false)
    private boolean synthetic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminTermsDocument() {
    }

    private AdminTermsDocument(
            String documentKey,
            String version,
            String content,
            boolean synthetic,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.documentKey = required(documentKey, "documentKey");
        this.version = required(version, "version");
        // The approved SHA identifies the exact UTF-8 content, including its
        // leading/trailing whitespace. Validate without normalizing the text.
        Objects.requireNonNull(content, "content");
        if (content.isBlank()) throw new IllegalArgumentException("content must not be blank");
        this.content = content;
        this.contentSha256 = AdminTermsContentHash.sha256(content);
        this.synthetic = synthetic;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static AdminTermsDocument publish(String documentKey, String version, String content, Instant publishedAt) {
        return new AdminTermsDocument(documentKey, version, content, false, publishedAt);
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    public String getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
