package pe.rutafija.operation.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.domain.OrganizationStatus;
import pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager;
import pe.rutafija.terms.application.AdminTermsService;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Additional regression of existing API prerequisites, not retrospective RED evidence. */
class OperationStreamAuthorizationTest {
    private final Organization organization = Organization.active("Synthetic authorization", "Test", "America/Lima");
    private final AppUser actor = AppUser.organizationUser(organization, "stream-auth@example.test", "hash", "Synthetic Admin", UserRole.ADMIN);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PersonIdentityGateAuthorizationManager identity = mock(PersonIdentityGateAuthorizationManager.class);
    private final AdminTermsService terms = mock(AdminTermsService.class);
    private OperationStreamIdentity captured;
    private OperationStreamAuthorization authorization;

    @BeforeEach
    void setup() {
        actor.verifyEmail(Instant.parse("2026-11-13T10:00:00Z"));
        captured = OperationStreamIdentity.from(actor);
        when(users.findByIdAndOrganization_Id(actor.getId(), organization.getId())).thenReturn(Optional.of(actor));
        when(identity.allowsUser(actor.getId())).thenReturn(true);
        when(terms.hasCurrentAcceptanceForActivation(actor.getId(), organization.getId())).thenReturn(true);
        authorization = new OperationStreamAuthorization(users, identity, terms, true);
    }

    @Test void currentVerifiedAdminWithBothGatesIsAllowed() { assertTrue(authorization.allows(captured)); }
    @Test void missingAccountIsDenied() {
        when(users.findByIdAndOrganization_Id(actor.getId(), organization.getId())).thenReturn(Optional.empty());
        assertFalse(authorization.allows(captured));
    }
    @Test void inactiveAccountIsDenied() { actor.deactivate(); assertFalse(authorization.allows(captured)); }
    @Test void inactiveOrganizationIsDenied() {
        organization.update("Synthetic authorization", "Test", "America/Lima", OrganizationStatus.INACTIVE);
        assertFalse(authorization.allows(captured));
    }
    @Test void nonAdminIsDenied() {
        ReflectionTestUtils.setField(actor, "role", UserRole.CONDUCTOR);
        assertFalse(authorization.allows(captured));
    }
    @Test void unverifiedEmailIsDenied() {
        actor.updateDetails("unverified@example.test", "Synthetic Admin", null, UserRole.ADMIN);
        assertFalse(authorization.allows(captured));
    }
    @Test void changedThenReverifiedEmailStillCannotAdoptOldTicket() {
        actor.updateDetails("new@example.test", "Synthetic Admin", null, UserRole.ADMIN);
        actor.verifyEmail(Instant.parse("2026-11-13T11:00:00Z"));
        assertFalse(authorization.allows(captured));
    }
    @Test void revokedSessionVersionIsDenied() {
        ReflectionTestUtils.setField(actor, "sessionVersion", actor.getSessionVersion()+1);
        assertFalse(authorization.allows(captured));
    }
    @Test void foreignOrganizationReturnedByRepositoryIsStillDenied() {
        Organization foreign = Organization.active("Foreign synthetic", "Foreign", "America/Lima");
        ReflectionTestUtils.setField(actor, "organization", foreign);
        assertFalse(authorization.allows(captured));
    }
    @Test void revokedPersonIdentityIsDenied() {
        when(identity.allowsUser(actor.getId())).thenReturn(false);
        assertFalse(authorization.allows(captured));
    }
    @Test void currentTermsNoLongerAcceptedIsDenied() {
        when(terms.hasCurrentAcceptanceForActivation(actor.getId(), organization.getId())).thenReturn(false);
        assertFalse(authorization.allows(captured));
    }
    @Test void explicitExistingTermsGateDisabledProfileIsHonored() {
        authorization = new OperationStreamAuthorization(users, identity, terms, false);
        assertTrue(authorization.allows(captured));
        verify(terms, never()).hasCurrentAcceptanceForActivation(actor.getId(), organization.getId());
    }
    @Test void missingIdentityIsDenied() { assertFalse(authorization.allows(null)); }
}
