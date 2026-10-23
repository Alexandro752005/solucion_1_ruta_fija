package pe.rutafija.personidentity.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import pe.rutafija.personidentity.infrastructure.PersonIdentityRepository;
import java.util.*;
import java.util.function.Supplier;

/** Missing legacy identity evidence grants only the exact session/bootstrap routes. */
@Component
public class PersonIdentityGateAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final PersonIdentityRepository repository;
    private final boolean enabled;
    public PersonIdentityGateAuthorizationManager(PersonIdentityRepository repository,
            @Value("${app.security.person-identity-gate.enabled:true}")boolean requested,Environment environment){
        this.repository=repository;
        this.enabled=requested||!Set.of(environment.getActiveProfiles()).equals(Set.of("test"));
    }
    /** Compatibility bridge for Spring Security's deprecated abstract contract. */
    @Override @Deprecated
    public AuthorizationDecision check(Supplier<Authentication> supplier,RequestAuthorizationContext context){
        return authorize(supplier, context);
    }
    @Override public AuthorizationDecision authorize(Supplier<Authentication> supplier,RequestAuthorizationContext context){
        Authentication auth=supplier.get();if(auth==null||!auth.isAuthenticated())return new AuthorizationDecision(false);
        if(!enabled)return new AuthorizationDecision(true);
        String method=context.getRequest().getMethod(),path=context.getRequest().getRequestURI();
        if(("GET".equals(method)&&Set.of("/api/v1/auth/me","/api/v1/identity/status").contains(path))||
                ("POST".equals(method)&&"/api/v1/platform/identity/self-enrollment".equals(path)))return new AuthorizationDecision(true);
        if(!(auth instanceof JwtAuthenticationToken jwt))return new AuthorizationDecision(false);
        try{return new AuthorizationDecision(repository.verified(UUID.fromString(jwt.getToken().getSubject())));}
        catch(IllegalArgumentException exception){return new AuthorizationDecision(false);}
    }
    public boolean allowsUser(UUID userId){return !enabled||repository.verified(userId);}
}
