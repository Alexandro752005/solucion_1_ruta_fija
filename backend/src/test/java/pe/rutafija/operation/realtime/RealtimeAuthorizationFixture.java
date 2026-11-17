package pe.rutafija.operation.realtime;

import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager;
import pe.rutafija.terms.application.AdminTermsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Positive fixture: the same identity and terms prerequisites as the API. */
final class RealtimeAuthorizationFixture {
    private RealtimeAuthorizationFixture() { }

    static OperationStreamAuthorization authorization(AppUserRepository users) {
        PersonIdentityGateAuthorizationManager identity = mock(PersonIdentityGateAuthorizationManager.class);
        AdminTermsService terms = mock(AdminTermsService.class);
        lenient().when(identity.allowsUser(any())).thenReturn(true);
        lenient().when(terms.hasCurrentAcceptanceForActivation(any(), any())).thenReturn(true);
        return new OperationStreamAuthorization(users, identity, terms, true);
    }
}
