package pe.rutafija.terms.application;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.AuthenticatedUser;
import pe.rutafija.shared.security.CurrentUserProvider;
import pe.rutafija.terms.api.dto.AcceptAdminTermsRequest;
import pe.rutafija.terms.api.dto.AdminTermsAcceptanceResponse;
import pe.rutafija.terms.api.dto.AdminTermsCurrentResponse;
import pe.rutafija.terms.domain.AdminTermsAcceptance;
import pe.rutafija.terms.domain.AdminTermsContentHash;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.infrastructure.AdminTermsAcceptanceRepository;
import pe.rutafija.terms.infrastructure.AdminTermsDocumentRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminTermsService {

    public static final String ADMIN_TERMS_DOCUMENT_KEY = "ADMIN_TERMS";

    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository userRepository;
    private final AdminTermsDocumentRepository documentRepository;
    private final AdminTermsAcceptanceRepository acceptanceRepository;
    private final AuditService auditService;
    private final Environment environment;
    private final Clock clock;

    public AdminTermsService(
            CurrentUserProvider currentUserProvider,
            AppUserRepository userRepository,
            AdminTermsDocumentRepository documentRepository,
            AdminTermsAcceptanceRepository acceptanceRepository,
            AuditService auditService,
            Environment environment,
            Clock clock
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.auditService = auditService;
        this.environment = environment;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminTermsCurrentResponse getCurrentTerms() {
        AuthenticatedUser actor = requireAdmin();
        AdminTermsDocument document = requireCurrentDocument();
        assertDocumentHash(document);

        Optional<AdminTermsAcceptance> acceptance = acceptanceRepository
                .findByAdminUser_IdAndDocument_Id(actor.userId(), document.getId());
        return new AdminTermsCurrentResponse(
                document.getDocumentKey(),
                document.getVersion(),
                document.getContent(),
                document.getContentSha256(),
                document.isSynthetic(),
                acceptance.isPresent(),
                acceptance.map(AdminTermsAcceptance::getAcceptedAt).orElse(null)
        );
    }

    @Transactional
    public AdminTermsAcceptanceResponse acceptCurrentTerms(AcceptAdminTermsRequest request) {
        AuthenticatedUser actor = requireAdmin();
        if (!Boolean.TRUE.equals(request.accepted())) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "La aceptación explícita debe ser verdadera"
            );
        }

        AdminTermsDocument document = requireCurrentDocument();
        assertDocumentHash(document);
        if (!document.getVersion().equals(request.version())
                || !constantTimeEquals(document.getContentSha256(), request.contentSha256())) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    "La versión o huella de términos ya no corresponde al documento vigente"
            );
        }

        AppUser admin = userRepository.findByIdForUpdate(actor.userId())
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_TOKEN_INVALID,
                        "La sesión no es válida"
                ));
        if (admin.getRole() != UserRole.ADMIN
                || !admin.isActive()
                || !admin.isEmailVerified()
                || admin.getOrganizationId() == null
                || !admin.getOrganizationId().equals(actor.organizationId())) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "Solo un ADMIN activo de una organización puede aceptar estos términos"
            );
        }

        Optional<AdminTermsAcceptance> existing = acceptanceRepository
                .findByAdminUser_IdAndDocument_Id(admin.getId(), document.getId());
        if (existing.isPresent()) {
            return response(existing.get());
        }

        Instant acceptedAt = Instant.now(clock);
        AdminTermsAcceptance acceptance = acceptanceRepository.save(AdminTermsAcceptance.record(
                admin,
                admin.getOrganization(),
                document,
                acceptedAt
        ));
        auditService.record(
                admin,
                "ADMIN_TERMS_ACCEPTED",
                "ADMIN_TERMS_DOCUMENT",
                document.getId(),
                Map.of(
                        "version", document.getVersion(),
                        "contentSha256", document.getContentSha256(),
                        "synthetic", document.isSynthetic()
                )
        );
        return response(acceptance);
    }

    /**
     * Returns whether the authenticated ADMIN accepted the exact current document
     * for the same organization. This is used by the request authorization gate;
     * absence of a published document or any mismatch fails closed.
     */
    @Transactional(readOnly = true)
    public boolean hasCurrentAcceptanceForCurrentActor() {
        AuthenticatedUser actor = currentUserProvider.requireCurrentUser();
        if (actor.role() != UserRole.ADMIN || actor.organizationId() == null) {
            return false;
        }

        AppUser admin = userRepository.findById(actor.userId()).orElse(null);
        if (admin == null
                || admin.getRole() != UserRole.ADMIN
                || !admin.isActive()
                || !admin.isEmailVerified()
                || !actor.organizationId().equals(admin.getOrganizationId())) {
            return false;
        }

        AdminTermsDocument document = currentDocumentForGate();
        if (document == null || !isDocumentHashValid(document)) {
            return false;
        }

        AdminTermsAcceptance acceptance = acceptanceRepository
                .findByAdminUser_IdAndDocument_Id(admin.getId(), document.getId())
                .orElse(null);
        return acceptance != null
                && acceptance.getOrganizationId().equals(actor.organizationId())
                && acceptance.getDocumentVersion().equals(document.getVersion())
                && constantTimeEquals(acceptance.getContentSha256(), document.getContentSha256())
                && acceptance.isSynthetic() == document.isSynthetic();
    }

    /** Internal entrypoint after an independently authenticated, purpose-scoped activation. */
    @Transactional(readOnly=true)
    public AdminTermsCurrentResponse getCurrentTermsForActivation(UUID userId,UUID organizationId) {
        AppUser admin=requireActivationAdmin(userId,organizationId,false);
        AdminTermsDocument document=requireCurrentDocument();assertDocumentHash(document);
        var acceptance=acceptanceRepository.findByAdminUser_IdAndDocument_Id(admin.getId(),document.getId());
        return new AdminTermsCurrentResponse(document.getDocumentKey(),document.getVersion(),document.getContent(),
                document.getContentSha256(),document.isSynthetic(),acceptance.isPresent(),
                acceptance.map(AdminTermsAcceptance::getAcceptedAt).orElse(null));
    }

    @Transactional
    public AdminTermsAcceptanceResponse acceptCurrentTermsForActivation(UUID userId,UUID organizationId,AcceptAdminTermsRequest request) {
        AppUser admin=requireActivationAdmin(userId,organizationId,true);
        if(!Boolean.TRUE.equals(request.accepted()))throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,
                "La aceptacion debe ser expresa y verdadera");
        AdminTermsDocument document=requireCurrentDocument();assertDocumentHash(document);
        if(!document.getVersion().equals(request.version())||!constantTimeEquals(document.getContentSha256(),request.contentSha256()))
            throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_VERSION_CONFLICT,
                    "La version o huella ya no corresponde a los terminos vigentes");
        var prior=acceptanceRepository.findByAdminUser_IdAndDocument_Id(userId,document.getId());
        if(prior.isPresent())return response(prior.get());
        var acceptance=acceptanceRepository.save(AdminTermsAcceptance.record(admin,admin.getOrganization(),document,Instant.now(clock)));
        auditService.record(admin,"ADMIN_ACTIVATION_TERMS_ACCEPTED","ADMIN_TERMS_DOCUMENT",document.getId(),
                Map.of("version",document.getVersion(),"contentSha256",document.getContentSha256(),"synthetic",document.isSynthetic()));
        return response(acceptance);
    }

    @Transactional(readOnly=true)
    public boolean hasCurrentAcceptanceForActivation(UUID userId,UUID organizationId) {
        AppUser admin=userRepository.findOneById(userId).orElse(null);
        if(admin==null||admin.getRole()!=UserRole.ADMIN||!admin.isEmailVerified()
                ||!organizationId.equals(admin.getOrganizationId())||!admin.getOrganization().isActive())return false;
        AdminTermsDocument document=currentDocumentForGate();
        if(document==null||!isDocumentHashValid(document))return false;
        var acceptance=acceptanceRepository.findByAdminUser_IdAndDocument_Id(userId,document.getId()).orElse(null);
        return acceptance!=null&&organizationId.equals(acceptance.getOrganizationId())
                &&document.getVersion().equals(acceptance.getDocumentVersion())
                &&constantTimeEquals(document.getContentSha256(),acceptance.getContentSha256())
                &&document.isSynthetic()==acceptance.isSynthetic();
    }

    private AppUser requireActivationAdmin(UUID userId,UUID organizationId,boolean lock) {
        AppUser admin=(lock?userRepository.findByIdForUpdate(userId):userRepository.findOneById(userId))
                .orElseThrow(()->new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"La activacion no esta autorizada"));
        if(admin.getRole()!=UserRole.ADMIN||admin.isActive()||!admin.isEmailVerified()
                ||!organizationId.equals(admin.getOrganizationId())||!admin.getOrganization().isActive())
            throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,
                    "Se requiere la cuenta ADMIN inactiva y el correo verificado en el flujo de activacion");
        return admin;
    }

    private AuthenticatedUser requireAdmin() {
        AuthenticatedUser actor = currentUserProvider.requireCurrentUser();
        if (actor.role() != UserRole.ADMIN || actor.organizationId() == null) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "Solo un ADMIN de una organización puede consultar o aceptar los términos"
            );
        }
        return actor;
    }

    private AdminTermsDocument requireCurrentDocument() {
        boolean testOnlyProfile = isTestOnlyProfile();
        Optional<AdminTermsDocument> document = testOnlyProfile
                ? documentRepository.findFirstByDocumentKeyOrderByCreatedAtDescIdDesc(ADMIN_TERMS_DOCUMENT_KEY)
                : documentRepository.findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc(
                        ADMIN_TERMS_DOCUMENT_KEY
                );
        AdminTermsDocument current = document.orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "Aún no hay una versión aprobada de términos ADMIN disponible"
        ));
        if (current.isSynthetic() && !testOnlyProfile) {
            throw new ApplicationException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Aún no hay una versión aprobada de términos ADMIN disponible"
            );
        }
        return current;
    }

    private AdminTermsDocument currentDocumentForGate() {
        boolean testOnlyProfile = isTestOnlyProfile();
        return (testOnlyProfile
                ? documentRepository.findFirstByDocumentKeyOrderByCreatedAtDescIdDesc(ADMIN_TERMS_DOCUMENT_KEY)
                : documentRepository.findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc(
                        ADMIN_TERMS_DOCUMENT_KEY
                )).orElse(null);
    }

    private boolean isTestOnlyProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 1 && Arrays.equals(activeProfiles, new String[]{"test"});
    }

    private void assertDocumentHash(AdminTermsDocument document) {
        String computedHash = AdminTermsContentHash.sha256(document.getContent());
        if (!constantTimeEquals(computedHash, document.getContentSha256())) {
            throw new ApplicationException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.INTERNAL_ERROR,
                    "La huella almacenada del documento de términos no es válida"
            );
        }
    }

    private boolean isDocumentHashValid(AdminTermsDocument document) {
        String computedHash = AdminTermsContentHash.sha256(document.getContent());
        return constantTimeEquals(computedHash, document.getContentSha256());
    }

    private AdminTermsAcceptanceResponse response(AdminTermsAcceptance acceptance) {
        return new AdminTermsAcceptanceResponse(
                acceptance.getId(),
                acceptance.getAdminUserId(),
                acceptance.getOrganizationId(),
                acceptance.getDocumentVersion(),
                acceptance.getContentSha256(),
                acceptance.isSynthetic(),
                acceptance.getAcceptedAt()
        );
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
