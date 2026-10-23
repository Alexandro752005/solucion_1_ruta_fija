package pe.rutafija.personidentity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Both gates stay enabled; only safe bootstrap status is readable. */
@SpringBootTest(properties={"debug=false","logging.level.root=INFO","logging.level.org.hibernate.SQL=INFO",
        "app.security.person-identity-gate.enabled=true","app.security.admin-terms-gate.enabled=true"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
class PersonIdentityTermsBootstrapIT extends NativePostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Test void unreviewedAdminCanReadOnlyBooleanBootstrapWithoutAcceptedTerms()throws Exception{
        var org=organizations.save(Organization.active("SYNTHETIC bootstrap "+UUID.randomUUID(),"Synthetic bootstrap","America/Lima"));
        var user=AppUser.organizationUser(org,"synthetic.bootstrap."+UUID.randomUUID()+"@rutafija.test",
                passwords.encode("Synthetic-Bootstrap-Password-2026"),"Synthetic ADMIN",UserRole.ADMIN);
        user.verifyEmail(Instant.now());user=users.saveAndFlush(user);
        String userId=user.getId().toString();
        var auth=jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")).jwt(token->token
                .subject(userId).claim("organizationId",org.getId().toString()).claim("role","ADMIN").claim("sessionVersion",0));
        mvc.perform(get("/api/v1/identity/status").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityVerified").value(false)).andExpect(jsonPath("$.documentNumber").doesNotExist());
        mvc.perform(get("/api/v1/groups").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/terms/current").with(auth)).andExpect(status().isForbidden());
    }
}
