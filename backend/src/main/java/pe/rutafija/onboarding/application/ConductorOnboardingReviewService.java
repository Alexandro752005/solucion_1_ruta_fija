package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingExpedienteResponse;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingRequestSummaryResponse;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConductorOnboardingReviewService {

    private final ConductorOnboardingRepository repository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public ConductorOnboardingReviewService(
            ConductorOnboardingRepository repository,
            CurrentUserService currentUserService,
            AuditService auditService
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ConductorOnboardingRequestSummaryResponse> list() {
        AppUser actor = requireAdmin();
        return repository.listRequestsForOrganization(actor.getOrganizationId()).stream()
                .map(request -> new ConductorOnboardingRequestSummaryResponse(
                        request.id(),
                        request.status(),
                        request.reviewRounds(),
                        request.submittedAt()
                ))
                .toList();
    }

    @Transactional
    public ConductorOnboardingExpedienteResponse expediente(UUID requestId) {
        AppUser actor = requireAdmin();
        var expediente = repository.findExpediente(requestId, actor.getOrganizationId())
                .orElseThrow(this::notFound);
        auditService.record(
                actor,
                "CONDUCTOR_ONBOARDING_EXPEDIENTE_VIEWED",
                "CONDUCTOR_ONBOARDING_REQUEST",
                requestId,
                Map.of()
        );
        return new ConductorOnboardingExpedienteResponse(
                expediente.id(),
                expediente.status(),
                expediente.fullName(),
                expediente.email(),
                expediente.phone()
        );
    }

    private AppUser requireAdmin() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN || !actor.isEmailVerified()) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La revisión del expediente requiere ADMIN verificado de la organización."
            );
        }
        return actor;
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "La solicitud de incorporación no existe."
        );
    }
}
