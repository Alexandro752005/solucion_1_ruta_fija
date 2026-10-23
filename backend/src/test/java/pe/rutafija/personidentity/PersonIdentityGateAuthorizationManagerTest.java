package pe.rutafija.personidentity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager;
import pe.rutafija.personidentity.infrastructure.PersonIdentityRepository;
import java.util.UUID;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PersonIdentityGateAuthorizationManagerTest {
    private final PersonIdentityRepository repository=mock(PersonIdentityRepository.class);
    @Test void disabledFlagCannotBypassNormalOrMixedProfiles(){
        for(String[] profiles:new String[][]{new String[0],{"local"},{"test","local"}}){
            MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles(profiles);
            var gate=new PersonIdentityGateAuthorizationManager(repository,false,environment);
            assertThat(gate.allowsUser(UUID.randomUUID())).isFalse();
        }
    }
    @Test void historicalFixturesMayDisableOnlyTheExactTestProfile(){
        MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles("test");
        assertThat(new PersonIdentityGateAuthorizationManager(repository,false,environment).allowsUser(UUID.randomUUID())).isTrue();
        assertThat(new PersonIdentityGateAuthorizationManager(repository,true,environment).allowsUser(UUID.randomUUID())).isFalse();
    }
    @Test void bootstrapAllowlistIsExactAndDoesNotOpenOtherMethodsOrSubpaths(){
        var gate=new PersonIdentityGateAuthorizationManager(repository,true,new MockEnvironment());
        var auth=new JwtAuthenticationToken(Jwt.withTokenValue("synthetic-token").header("alg","HS256")
                .subject(UUID.randomUUID().toString()).claim("role","SUPER_ADMIN").build(),
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        for(String[] route:new String[][]{{"GET","/api/v1/auth/me"},{"GET","/api/v1/identity/status"},{"POST","/api/v1/platform/identity/self-enrollment"}})
            assertThat(gate.authorize(()->auth,new RequestAuthorizationContext(new MockHttpServletRequest(route[0],route[1]))).isGranted()).isTrue();
        for(String[] route:new String[][]{{"POST","/api/v1/auth/me"},{"GET","/api/v1/platform/admins"},{"POST","/api/v1/platform/identity/self-enrollment/extra"}})
            assertThat(gate.authorize(()->auth,new RequestAuthorizationContext(new MockHttpServletRequest(route[0],route[1]))).isGranted()).isFalse();
    }

    @Test void anUnauthenticatedJwtCannotUseEvenTheBootstrapRoutes(){
        var gate=new PersonIdentityGateAuthorizationManager(repository,true,new MockEnvironment());
        var unauthenticated=new JwtAuthenticationToken(Jwt.withTokenValue("synthetic-unverified-token")
                .header("alg","HS256").subject(UUID.randomUUID().toString()).build());
        assertThat(unauthenticated.isAuthenticated()).isFalse();
        assertThat(gate.authorize(()->unauthenticated,new RequestAuthorizationContext(
                new MockHttpServletRequest("GET","/api/v1/identity/status"))).isGranted()).isFalse();
    }

    @Test void legacyBridgeAndModernAuthorizationPreserveTheSameAllowAndDenyDecisions() throws Exception {
        var gate=new PersonIdentityGateAuthorizationManager(repository,true,new MockEnvironment());
        var auth=new JwtAuthenticationToken(Jwt.withTokenValue("synthetic-token").header("alg","HS256")
                .subject(UUID.randomUUID().toString()).build(), List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        var legacy=PersonIdentityGateAuthorizationManager.class.getMethod("check",java.util.function.Supplier.class,RequestAuthorizationContext.class);
        assertThat(legacy.isAnnotationPresent(Deprecated.class)).isTrue();
        java.util.function.Supplier<org.springframework.security.core.Authentication> supplier=()->auth;
        for(String path:List.of("/api/v1/auth/me","/api/v1/platform/admins")){
            var context=new RequestAuthorizationContext(new MockHttpServletRequest("GET",path));
            var oldDecision=(org.springframework.security.authorization.AuthorizationDecision)legacy.invoke(gate,supplier,context);
            assertThat(oldDecision.isGranted()).isEqualTo(gate.authorize(supplier,context).isGranted());
        }
    }
}
