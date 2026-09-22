package pe.rutafija.audit.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.api.dto.AuditEventResponse;
import pe.rutafija.audit.domain.AuditEvent;
import pe.rutafija.audit.infrastructure.AuditEventRepository;
import pe.rutafija.audit.infrastructure.AuditEventSpecifications;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Consulta inmutable, aislada por organización, para el registro de auditoría. */
@Service
public class AuditQueryService {

    private static final Set<String> SENSITIVE_METADATA_KEY_FRAGMENTS = Set.of(
            "password", "secret", "token", "authorization", "cookie", "latitude", "longitude", "location"
    );

    private final AuditEventRepository repository;
    private final CurrentUserService currentUserService;

    public AuditQueryService(AuditEventRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(
            String action,
            String entityType,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        AppUser actor = requireAdmin();
        validateRange(from, to);
        Page<AuditEvent> events = repository.findAll(
                AuditEventSpecifications.filter(actor.getOrganizationId(), action, entityType, from, to),
                pageable
        );
        return PageResponse.from(events, this::response);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse get(UUID auditEventId) {
        AppUser actor = requireAdmin();
        AuditEvent event = repository.findOne(AuditEventSpecifications.byIdAndOrganization(
                        auditEventId,
                        actor.getOrganizationId()
                ))
                .orElseThrow(this::notFound);
        return response(event);
    }

    private AppUser requireAdmin() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La consulta de auditoría requiere el rol ADMIN"
            );
        }
        return actor;
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "La fecha final de auditoría no puede ser anterior a la inicial"
            );
        }
    }

    private AuditEventResponse response(AuditEvent event) {
        return AuditEventResponse.from(event, safeMetadata(event.getMetadata()));
    }

    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (!isSensitiveKey(key)) {
                safe.put(key, value);
            }
        });
        return Map.copyOf(safe);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        return SENSITIVE_METADATA_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El evento de auditoría no existe en la organización activa"
        );
    }
}
