package pe.rutafija.invitation.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.invitation.api.dto.CreatedInvitationResponse;
import pe.rutafija.invitation.api.dto.InvitationSummaryResponse;
import pe.rutafija.invitation.infrastructure.InvitationJdbcRepository;
import pe.rutafija.invitation.infrastructure.InvitationJdbcRepository.InvitationMatch;
import pe.rutafija.invitation.infrastructure.InvitationJdbcRepository.InvitationRow;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.AuthenticatedUser;
import pe.rutafija.shared.security.CurrentUserProvider;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InvitationService {

    private static final Duration INVITATION_TTL = Duration.ofHours(120);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration ACCOUNT_BLOCK = Duration.ofMinutes(30);
    private static final Duration ORIGIN_PAUSE = Duration.ofMinutes(15);
    private static final long ACCOUNT_FAILURE_LIMIT = 5;
    private static final long ORIGIN_FAILURE_LIMIT = 50;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 100;

    private final InvitationJdbcRepository repository;
    private final InvitationCodeGenerator codeGenerator;
    private final InvitationCodeHasher codeHasher;
    private final CurrentUserService currentUserService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    public InvitationService(
            InvitationJdbcRepository repository,
            InvitationCodeGenerator codeGenerator,
            InvitationCodeHasher codeHasher,
            CurrentUserService currentUserService,
            CurrentUserProvider currentUserProvider,
            AuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.codeHasher = codeHasher;
        this.currentUserService = currentUserService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CreatedInvitationResponse create(String requestedRecipientEmail) {
        AppUser actor = requireAdminActor();
        String recipientEmail = normalizeEmail(requestedRecipientEmail);
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(INVITATION_TTL);

        // The tenant row serializes reissues for a recipient in the same organization.
        repository.lockOrganization(actor.getOrganizationId());
        int invalidated = repository.revokePriorInvitations(
                actor.getOrganizationId(),
                recipientEmail,
                issuedAt
        );

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            InvitationRow invitation = new InvitationRow(
                    UUID.randomUUID(),
                    actor.getOrganizationId(),
                    actor.getId(),
                    recipientEmail,
                    codeHasher.hashCode(code, recipientEmail),
                    issuedAt,
                    expiresAt,
                    null,
                    null
            );
            if (repository.insertInvitation(invitation)) {
                auditService.record(actor, "INVITATION_ISSUED", "ORGANIZATION_INVITATION", invitation.id(), Map.of(
                        "invalidatedPriorCount", invalidated,
                        "expiresAt", expiresAt.toString()
                ));
                return new CreatedInvitationResponse(
                        invitation.id(),
                        "",
                        code,
                        issuedAt,
                        expiresAt
                );
            }
        }
        throw new IllegalStateException("Unable to generate a unique active invitation code");
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationSummaryResponse> list(int page, int size) {
        AppUser actor = requireAdminActor();
        var pageable = PageRequest.of(page, size);
        var rows = repository.listTenantInvitations(
                actor.getOrganizationId(),
                pageable.getPageSize(),
                Math.toIntExact(pageable.getOffset())
        );
        long total = repository.countTenantInvitations(actor.getOrganizationId());
        int totalPages = total == 0 ? 0 : Math.toIntExact((total + size - 1) / size);
        return new PageResponse<>(
                rows.stream().map(InvitationService::summary).toList(),
                page,
                size,
                total,
                totalPages
        );
    }

    @Transactional
    public InvitationSummaryResponse revoke(UUID invitationId) {
        AppUser actor = requireAdminActor();
        InvitationRow invitation = repository.findTenantInvitationForUpdate(
                        actor.getOrganizationId(),
                        invitationId
                )
                .orElseThrow(this::notFound);
        if (invitation.revokedAt() == null && invitation.usedAt() == null) {
            Instant revokedAt = Instant.now(clock);
            if (repository.revokeInvitation(actor.getOrganizationId(), invitationId, revokedAt)) {
                auditService.record(actor, "INVITATION_REVOKED", "ORGANIZATION_INVITATION", invitationId, Map.of());
                invitation = new InvitationRow(
                        invitation.id(),
                        invitation.organizationId(),
                        invitation.issuerUserId(),
                        invitation.recipientEmail(),
                        invitation.codeHash(),
                        invitation.issuedAt(),
                        invitation.expiresAt(),
                        revokedAt,
                        invitation.usedAt()
                );
            }
        }
        return summary(invitation);
    }

    @Transactional
    public InvitationValidationResult validate(String code, String remoteAddress) {
        AuthenticatedUser authenticated = currentUserProvider.requireCurrentUser();
        if (authenticated.organizationId() == null || authenticated.role() != UserRole.CONDUCTOR) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La validación de invitaciones requiere una cuenta CONDUCTOR"
            );
        }

        Instant now = Instant.now(clock);
        String recipientEmail = normalizeEmail(authenticated.email());
        String originHash = codeHasher.hashOrigin(remoteAddress);
        Instant accountBlockedUntil = repository.lockAccountGuard(authenticated.userId(), now);
        Instant originPausedUntil = repository.lockOriginGuard(originHash, now);

        Instant blockedUntil = laterActiveBlock(accountBlockedUntil, originPausedUntil, now);
        if (blockedUntil != null) {
            return InvitationValidationResult.limited(blockedUntil);
        }

        repository.pruneFailuresAtOrBefore(now.minus(FAILURE_WINDOW));
        InvitationMatch match = null;
        if (code != null && code.matches("[0-9]{6}")) {
            String expectedHash = codeHasher.hashCode(code, recipientEmail);
            match = repository.findActiveInvitation(recipientEmail, expectedHash, now).orElse(null);
            if (match != null && !codeHasher.matchesCode(code, recipientEmail, match.codeHash())) {
                match = null;
            }
        }
        if (match != null) {
            return InvitationValidationResult.valid(match.id());
        }

        repository.recordFailure(authenticated.userId(), originHash, now);
        Instant cutoff = now.minus(FAILURE_WINDOW);
        long accountFailures = repository.countFailuresForAccountSince(authenticated.userId(), cutoff);
        long originFailures = repository.countFailuresForOriginSince(originHash, cutoff);
        Instant accountBlock = null;
        Instant originPause = null;
        if (accountFailures >= ACCOUNT_FAILURE_LIMIT) {
            accountBlock = now.plus(ACCOUNT_BLOCK);
            repository.blockAccount(authenticated.userId(), accountBlock, now);
        }
        if (originFailures >= ORIGIN_FAILURE_LIMIT) {
            originPause = now.plus(ORIGIN_PAUSE);
            repository.pauseOrigin(originHash, originPause, now);
        }

        Instant retryAt = later(accountBlock, originPause);
        return retryAt == null
                ? InvitationValidationResult.invalid()
                : InvitationValidationResult.limited(retryAt);
    }

    private AppUser requireAdminActor() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN || !actor.isEmailVerified()) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La operación requiere una cuenta ADMIN verificada"
            );
        }
        return actor;
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    static Instant laterActiveBlock(Instant accountBlock, Instant originPause, Instant now) {
        Instant account = accountBlock != null && accountBlock.isAfter(now) ? accountBlock : null;
        Instant origin = originPause != null && originPause.isAfter(now) ? originPause : null;
        return later(account, origin);
    }

    private static Instant later(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null || left.isAfter(right)) {
            return left;
        }
        return right;
    }

    private static InvitationSummaryResponse summary(InvitationRow invitation) {
        return new InvitationSummaryResponse(
                invitation.id(),
                "",
                invitation.issuedAt(),
                invitation.expiresAt(),
                invitation.revokedAt(),
                invitation.usedAt()
        );
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "La invitación solicitada no existe"
        );
    }
}
