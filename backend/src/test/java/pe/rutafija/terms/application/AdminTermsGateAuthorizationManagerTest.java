package pe.rutafija.terms.application;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import java.util.List;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminTermsGateAuthorizationManagerTest {
    private final AdminTermsService terms=mock(AdminTermsService.class);
    private final AdminTermsGateAuthorizationManager gate=new AdminTermsGateAuthorizationManager(terms,true);

    private Authentication actor(String role){
        return UsernamePasswordAuthenticationToken.authenticated("synthetic","synthetic",List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    private RequestAuthorizationContext request(String method,String path){
        return new RequestAuthorizationContext(new MockHttpServletRequest(method,path));
    }

    @Test void missingAndUnauthenticatedActorsRemainDeniedBeforeBootstrap(){
        assertThat(gate.authorize(()->null,request("GET","/api/v1/auth/me")).isGranted()).isFalse();
        assertThat(gate.authorize(()->UsernamePasswordAuthenticationToken.unauthenticated("synthetic","synthetic"),request("GET","/api/v1/auth/me")).isGranted()).isFalse();
        verifyNoInteractions(terms);
    }

    @Test void exactAdminBootstrapAndOtherRolesDoNotQueryPersonalAcceptance(){
        for(String path:List.of("/api/v1/auth/me","/api/v1/identity/status"))
            assertThat(gate.authorize(()->actor("ADMIN"),request("GET",path)).isGranted()).isTrue();
        for(String role:List.of("SUPER_ADMIN","CONDUCTOR"))
            assertThat(gate.authorize(()->actor(role),request("GET","/api/v1/groups")).isGranted()).isTrue();
        verifyNoInteractions(terms);
    }

    @Test void adminOperationsAndBootstrapLookalikesRequireCurrentPersonalAcceptance(){
        for(var context:List.of(request("GET","/api/v1/groups"),request("POST","/api/v1/auth/me"),request("GET","/api/v1/identity/status/extra")))
            assertThat(gate.authorize(()->actor("ADMIN"),context).isGranted()).isFalse();
        when(terms.hasCurrentAcceptanceForCurrentActor()).thenReturn(true);
        assertThat(gate.authorize(()->actor("ADMIN"),request("GET","/api/v1/groups")).isGranted()).isTrue();
    }

    @Test void revokedSessionStillBecomesAnAuthenticationFailure(){
        when(terms.hasCurrentAcceptanceForCurrentActor()).thenThrow(new ApplicationException(HttpStatus.UNAUTHORIZED,ErrorCode.AUTH_TOKEN_INVALID,"synthetic"));
        assertThatThrownBy(()->gate.authorize(()->actor("ADMIN"),request("GET","/api/v1/groups")))
                .isInstanceOf(InsufficientAuthenticationException.class).hasMessage("La sesión ya no es válida.");
    }

    @Test void forbiddenPersonalAcceptanceRemainsDeniedAndUnexpectedErrorsArePropagated(){
        when(terms.hasCurrentAcceptanceForCurrentActor()).thenThrow(new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.TENANT_ACCESS_DENIED,"synthetic"));
        assertThat(gate.authorize(()->actor("ADMIN"),request("GET","/api/v1/groups")).isGranted()).isFalse();
        var unexpected=new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE,ErrorCode.SERVICE_UNAVAILABLE,"synthetic");
        org.mockito.Mockito.doThrow(unexpected).when(terms).hasCurrentAcceptanceForCurrentActor();
        assertThatThrownBy(()->gate.authorize(()->actor("ADMIN"),request("GET","/api/v1/groups"))).isSameAs(unexpected);
    }

    @Test void explicitDisabledFlagStillRequiresAnAuthenticatedActor(){
        var disabled=new AdminTermsGateAuthorizationManager(terms,false);
        assertThat(disabled.authorize(()->actor("ADMIN"),request("GET","/api/v1/groups")).isGranted()).isTrue();
        assertThat(disabled.authorize(()->null,request("GET","/api/v1/groups")).isGranted()).isFalse();
        verifyNoInteractions(terms);
    }

    @Test void deprecatedBridgeKeepsExactlyTheModernDecisionForAllowedAndDeniedRequests() throws Exception {
        var legacy=AdminTermsGateAuthorizationManager.class.getMethod("check",Supplier.class,RequestAuthorizationContext.class);
        assertThat(legacy.isAnnotationPresent(Deprecated.class)).isTrue();
        Supplier<Authentication> admin=()->actor("ADMIN");
        for(String path:List.of("/api/v1/auth/me","/api/v1/groups")){
            var context=request("GET",path);
            var decision=(AuthorizationDecision)legacy.invoke(gate,admin,context);
            assertThat(decision.isGranted()).isEqualTo(gate.authorize(admin,context).isGranted());
        }
    }
}
