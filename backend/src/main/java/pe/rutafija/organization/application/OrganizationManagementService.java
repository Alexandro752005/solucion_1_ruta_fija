package pe.rutafija.organization.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.organization.api.dto.OrganizationCreateRequest;
import pe.rutafija.organization.api.dto.OrganizationResponse;
import pe.rutafija.organization.api.dto.OrganizationUpdateRequest;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganizationManagementService {

    private final OrganizationRepository organizationRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public OrganizationManagementService(
            OrganizationRepository organizationRepository,
            CurrentUserService currentUserService,
            AuditService auditService
    ) {
        this.organizationRepository = organizationRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> list(String search, Pageable pageable) {
        currentUserService.requireSuperAdminActor();
        Page<Organization> organizations = normalizeSearch(search) == null
                ? organizationRepository.findAll(pageable)
                : organizationRepository.findByLegalNameContainingIgnoreCase(normalizeSearch(search), pageable);
        return PageResponse.from(organizations, OrganizationResponse::from);
    }

    @Transactional
    public OrganizationResponse create(OrganizationCreateRequest request) {
        var actor = currentUserService.requireSuperAdminActor();
        if (organizationRepository.existsByLegalNameIgnoreCase(request.legalName().strip())) {
            throw conflict("Ya existe una organización con esa razón social");
        }

        Organization organization = organizationRepository.save(Organization.active(
                request.legalName(),
                request.tradeName(),
                request.timezone()
        ));
        auditService.record(
                actor,
                "ORGANIZATION_CREATED",
                "ORGANIZATION",
                organization.getId(),
                Map.of("status", organization.getStatus().name())
        );
        return OrganizationResponse.from(organization);
    }

    @Transactional
    public OrganizationResponse update(UUID organizationId, OrganizationUpdateRequest request) {
        var actor = currentUserService.requireSuperAdminActor();
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(this::notFound);
        if (!organization.getLegalName().equalsIgnoreCase(request.legalName().strip())
                && organizationRepository.existsByLegalNameIgnoreCase(request.legalName().strip())) {
            throw conflict("Ya existe una organización con esa razón social");
        }

        organization.update(
                request.legalName(),
                request.tradeName(),
                request.timezone(),
                request.status()
        );
        auditService.record(
                actor,
                "ORGANIZATION_UPDATED",
                "ORGANIZATION",
                organization.getId(),
                Map.of("status", organization.getStatus().name())
        );
        return OrganizationResponse.from(organization);
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.strip().toLowerCase(Locale.ROOT);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "La organización solicitada no existe"
        );
    }

    private ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
