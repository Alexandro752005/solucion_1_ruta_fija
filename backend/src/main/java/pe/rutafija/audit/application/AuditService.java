package pe.rutafija.audit.application;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.domain.AuditEvent;
import pe.rutafija.audit.infrastructure.AuditEventRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;
    private final AuditMetadataPolicy metadataPolicy;

    public AuditService(AuditEventRepository repository, Clock clock, AuditMetadataPolicy metadataPolicy) {
        this.repository = repository;
        this.clock = clock;
        this.metadataPolicy = metadataPolicy;
    }

    @Transactional
    public void record(AppUser user, String action, Map<String, Object> metadata) {
        repository.save(new AuditEvent(
                user == null ? null : user.getOrganization(),
                user,
                action,
                user == null ? null : "APP_USER",
                user == null ? null : user.getId(),
                AuditCorrelationId.safe(MDC.get(CorrelationIdFilter.MDC_KEY)),
                safeMetadata(action, metadata),
                Instant.now(clock)
        ));
    }

    @Transactional
    public void record(
            AppUser user,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> metadata
    ) {
        repository.save(new AuditEvent(
                user == null ? null : user.getOrganization(),
                user,
                action,
                entityType,
                entityId,
                AuditCorrelationId.safe(MDC.get(CorrelationIdFilter.MDC_KEY)),
                safeMetadata(action, metadata),
                Instant.now(clock)
        ));
    }

    /** Records a backend-derived fact while retaining tenant visibility but no impersonated user. */
    @Transactional
    public void recordSystem(
            Organization organization,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> metadata
    ) {
        repository.save(new AuditEvent(
                organization,
                null,
                action,
                entityType,
                entityId,
                AuditCorrelationId.safe(MDC.get(CorrelationIdFilter.MDC_KEY)),
                safeMetadata(action, metadata),
                Instant.now(clock)
        ));
    }
    private Map<String,Object> safeMetadata(String action,Map<String,Object> metadata){
        var safe=new java.util.LinkedHashMap<>(metadataPolicy.sanitize(metadata));
        String outcome=action.contains("DENIED")||action.contains("LOGIN_FAILURE")||action.equals("REAUTH_FAILED")||action.endsWith("REFRESH_REUSE_DETECTED")||action.startsWith("SUPER_ADMIN_RECOVERY_")&&action.endsWith("REJECTED")?"DENIED":action.endsWith("FAILED")?"FAILED":"SUCCEEDED";
        safe.put("auditOutcome",outcome);return Map.copyOf(safe);
    }
}
