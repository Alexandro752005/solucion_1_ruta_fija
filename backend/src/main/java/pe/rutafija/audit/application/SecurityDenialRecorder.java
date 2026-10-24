package pe.rutafija.audit.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import java.util.*;

/** Runs after the business transaction. REQUIRED also keeps MockMvc rollback fixtures isolated. */
@Service
public class SecurityDenialRecorder {
    private final AuditService audit;private final AppUserRepository users;
    public SecurityDenialRecorder(AuditService audit,AppUserRepository users){this.audit=audit;this.users=users;}
    @Transactional public void record(Authentication authentication,int status,String method,String module){
        AppUser actor=null;
        if(authentication instanceof JwtAuthenticationToken jwt&&authentication.isAuthenticated()&&!authentication.getAuthorities().isEmpty()){
            try{
                var user=users.findOneById(UUID.fromString(jwt.getToken().getSubject())).orElse(null);
                String orgClaim=jwt.getToken().getClaimAsString("organizationId");UUID org=orgClaim==null?null:UUID.fromString(orgClaim);
                Object version=jwt.getToken().getClaims().get("sessionVersion");boolean sameVersion=user!=null&&(version instanceof Number n?user.getSessionVersion()==n.longValue():user.getSessionVersion()==0);
                String email=jwt.getToken().getClaimAsString("email");
                if(user!=null&&user.isActive()&&user.isEmailVerified()&&sameVersion&&Objects.equals(user.getOrganizationId(),org)&&(email==null||email.equals(user.getEmail()))&&user.getRole().name().equals(jwt.getToken().getClaimAsString("role")))actor=user;
            }catch(IllegalArgumentException ignored){}
        }
        audit.record(actor,"SECURITY_ACCESS_DENIED","HTTP_REQUEST",null,Map.of("httpStatus",status,"method",method,"module",module));
    }
}
