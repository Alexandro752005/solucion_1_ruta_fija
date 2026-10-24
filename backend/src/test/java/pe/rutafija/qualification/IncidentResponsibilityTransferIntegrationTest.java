package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.rutafija.support.L02PostgresIntegrationTest;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.fleet.domain.*;
import pe.rutafija.fleet.infrastructure.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** One extra ADMIN exists only in the enclosing rollback. No persistent account or case. */
@SpringBootTest(properties={"debug=false","logging.level.root=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
class IncidentResponsibilityTransferIntegrationTest extends L02PostgresIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JwtService jwt;
    @Autowired AppUserRepository users; @Autowired TransportGroupRepository groups; @Autowired DriverRepository drivers;
    @MockitoBean OtpEmailSender mail;
    @Test void incidentTransferBetweenDifferentActiveAdminsKeepsBothRounds() throws Exception {
        var incumbent=users.findById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow();
        assertThat(incumbent.isActive()).isTrue();assertThat(incumbent.getRole()).isEqualTo(UserRole.ADMIN);
        int before=jdbc.queryForObject("select count(*) from pilot_account_creation where role='ADMIN'",Integer.class);
        assertThat(before).isLessThan(4);
        var replacement=users.saveAndFlush(AppUser.organizationUser(incumbent.getOrganization(),"transfer."+UUID.randomUUID()+"@rutafija.test","SYNTHETIC-NONLOGIN-HASH","SYNTHETIC rollback target",UserRole.ADMIN));
        var group=groups.saveAndFlush(TransportGroup.active(incumbent.getOrganization(),"SYNTHETIC rollback incident group "+UUID.randomUUID(),null));
        var driver=drivers.saveAndFlush(Driver.register(incumbent.getOrganization(),null,group,"SYNTHETIC rollback driver",null,"DNI","79999991",null));
        var token=jwt.issueAccessToken(incumbent);
        var result=mvc.perform(post("/api/v1/incidents").header("Authorization","Bearer "+token).contentType("application/json").content(mapper.writeValueAsBytes(Map.of("driverId",driver.getId(),"category","RETRASO","severity","LOW","description","SYNTHETIC transfer case","operationKey",UUID.randomUUID())))).andExpect(status().isCreated()).andReturn();
        var incident=mapper.readTree(result.getResponse().getContentAsByteArray());String id=incident.path("id").asText();
        for(var target:List.of(incumbent,replacement)) {
            result=mvc.perform(post("/api/v1/incidents/"+id+"/responsibility").header("Authorization","Bearer "+token).contentType("application/json").content(mapper.writeValueAsBytes(Map.of("operationKey",UUID.randomUUID(),"expectedVersion",incident.path("version").asLong(),"adminId",target.getId(),"reason","SYNTHETIC checked handover")))).andExpect(status().isOk()).andReturn();
            incident=mapper.readTree(result.getResponse().getContentAsByteArray());
        }
        assertThat(incident.path("responsibleAdminId").asText()).isEqualTo(replacement.getId().toString());
        result=mvc.perform(get("/api/v1/incidents/"+id+"/history").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn();
        var history=mapper.readTree(result.getResponse().getContentAsByteArray());assertThat(history).hasSize(3);
        assertThat(history.get(2).path("detail").path("previousAdminId").asText()).isEqualTo(incumbent.getId().toString());
        assertThat(history.get(2).path("detail").path("adminId").asText()).isEqualTo(replacement.getId().toString());
        assertThat(jdbc.queryForObject("select count(*) from pilot_account_creation where role='ADMIN'",Integer.class)).isEqualTo(before+1);
    }
}
