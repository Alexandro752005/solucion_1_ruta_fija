package pe.rutafija.operation.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager;
import pe.rutafija.terms.application.AdminTermsService;

/** Rechecks the account and existing API gates without relying on a handshake-era entity. */
@Component
public class OperationStreamAuthorization {
    private final AppUserRepository users;
    private final PersonIdentityGateAuthorizationManager identityGate;
    private final AdminTermsService terms;
    private final boolean termsGateEnabled;

    public OperationStreamAuthorization(AppUserRepository users,
            PersonIdentityGateAuthorizationManager identityGate, AdminTermsService terms,
            @Value("${app.security.admin-terms-gate.enabled:true}") boolean termsGateEnabled) {
        this.users = users;
        this.identityGate = identityGate;
        this.terms = terms;
        this.termsGateEnabled = termsGateEnabled;
    }

    // AFTER_COMMIT listeners may retain the publisher's persistence context.
    // A new transaction prevents authorization from reading that stale entity.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean allows(OperationStreamIdentity identity) {
        if (identity == null || identity.organizationId() == null || identity.userId() == null
                || identity.email() == null) {
            return false;
        }
        AppUser actor = users.findByIdAndOrganization_Id(identity.userId(), identity.organizationId()).orElse(null);
        return actor != null && actor.isActive() && actor.isEmailVerified()
                && actor.getRole() == UserRole.ADMIN && actor.getOrganization() != null
                && actor.getOrganization().isActive()
                && identity.organizationId().equals(actor.getOrganizationId())
                && identity.sessionVersion() == actor.getSessionVersion()
                && identity.email().equals(actor.getEmail())
                && identityGate.allowsUser(identity.userId())
                && (!termsGateEnabled || terms.hasCurrentAcceptanceForActivation(identity.userId(), identity.organizationId()));
    }
}
