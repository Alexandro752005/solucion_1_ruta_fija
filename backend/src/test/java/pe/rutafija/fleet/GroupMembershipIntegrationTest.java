package pe.rutafija.fleet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import pe.rutafija.support.L02PostgresIntegrationTest;
import pe.rutafija.fleet.domain.*;
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP filters and PostgreSQL; technical resources roll back. No accounts or acceptance are created. */
@SpringBootTest(properties={"logging.level.root=ERROR","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("test")
class GroupMembershipIntegrationTest extends L02PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired TransportGroupRepository groups;
    @Autowired DriverRepository drivers;
    @Autowired VehicleRepository vehicles;
    @Autowired AssignmentRepository assignments;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @MockitoBean OtpEmailSender mail;
    private AppUser admin;
    private Organization org,foreign;
    private TransportGroup home,second,foreignGroup;
    private Driver driver;
    private Vehicle vehicle,foreignVehicle;
    private String token,ledger;

    @BeforeEach void resources(){
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success and version='29'",String.class)).isEqualTo("29");
        admin=users.findOneById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow();
        org=admin.getOrganization();token=jwt.issueAccessToken(admin);
        ledger=jdbc.queryForObject("select count(*)::text from pilot_account_creation",String.class);
        String suffix=UUID.randomUUID().toString().substring(0,8);
        foreign=organizations.saveAndFlush(Organization.active("Synthetic L03 other "+suffix,null,"America/Lima"));
        home=groups.saveAndFlush(TransportGroup.active(org,"Synthetic L03 home "+suffix,null));
        second=groups.saveAndFlush(TransportGroup.active(org,"Synthetic L03 second "+suffix,null));
        foreignGroup=groups.saveAndFlush(TransportGroup.active(foreign,"Synthetic L03 foreign "+suffix,null));
        driver=drivers.saveAndFlush(Driver.register(org,null,home,"Synthetic Group Person",null,"SYNTHETIC","SYN-"+suffix,null));
        driver.deactivate();drivers.flush();
        vehicle=vehicles.saveAndFlush(Vehicle.register(org,"G-"+suffix,null,null,null,null));
        foreignVehicle=vehicles.saveAndFlush(Vehicle.register(foreign,"F-"+suffix,null,null,null,null));
    }
    @AfterEach void noQuotaOrMail(){assertThat(jdbc.queryForObject("select count(*)::text from pilot_account_creation",String.class)).isEqualTo(ledger);verifyNoInteractions(mail);}

    @Test void oneDriverCanBelongToSeveralGroupsWithoutCreatingAUnitLinkOrPrivilege()throws Exception{
        change(second,command(0,"add-driver-one","DRIVER",driver.getId(),true)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/groups/{id}/drivers",second.getId()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        assertThat(jdbc.queryForObject("select count(*) from group_driver_membership where driver_id=? and active",Integer.class,driver.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from driver_vehicle_link where driver_id=?",Integer.class,driver.getId())).isZero();
        assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,driver.getId())).isFalse();
    }
    @Test void vehicleMembershipDoesNotCreateDriverVehiclePeriodAndQueryHasNoPersonalFields()throws Exception{
        change(second,command(0,"add-vehicle-one","VEHICLE",vehicle.getId(),true)).andExpect(status().isOk());
        String body=read(second).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(driver.getFullName(),driver.getDocumentNumber(),admin.getEmail(),"fullName","documentNumber","phone");
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where vehicle_id=?",Integer.class,vehicle.getId())).isZero();
    }
    @Test void exactRetryIsIdempotentAndChangedPayloadOrStaleVersionIsRejected()throws Exception{
        var c=command(0,"same-membership-key","DRIVER",driver.getId(),true);
        change(second,c).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        change(second,c).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        change(second,command(0,"same-membership-key","DRIVER",driver.getId(),false)).andExpect(status().isConflict());
        change(second,command(0,"stale-membership-key","VEHICLE",vehicle.getId(),true)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from group_membership_operation where group_id=?",Integer.class,second.getId())).isEqualTo(1);
    }
    @Test void foreignResourcesAndGroupsAreHiddenAndSuperAdminCannotOperateTenantMemberships()throws Exception{
        read(foreignGroup).andExpect(status().isNotFound());
        change(second,command(0,"foreign-unit-key","VEHICLE",foreignVehicle.getId(),true)).andExpect(status().isNotFound());
        mvc.perform(get(path(second)+"/resources")).andExpect(status().isUnauthorized());
        String superToken=jwt.issueAccessToken(users.findOneById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow());
        mvc.perform(get(path(second)+"/resources").header("Authorization","Bearer "+superToken)).andExpect(status().isForbidden());
    }
    @Test void invalidCommandsDoNotChangeMembershipsOrVersion()throws Exception{
        var c=command(0,"invalid-command-key","DRIVER",driver.getId(),true);c.remove("expectedVersion");
        change(second,c).andExpect(status().isBadRequest());
        c=command(0,"invalid-command-two","VEHICLE",vehicle.getId(),true);c.put("resourceKind","ADMIN");change(second,c).andExpect(status().isBadRequest());
        c=command(0,"invalid-command-three","DRIVER",driver.getId(),true);c.put("reason","contact@rutafija.test");change(second,c).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select version from transport_group where id=?",Long.class,second.getId())).isZero();
    }
    @Test void pendingServiceAndOpenReassignmentAreVisibleAndBlockRetirement()throws Exception{
        Assignment a=assignment();
        read(home).andExpect(status().isOk()).andExpect(jsonPath("$.commitments[0].assignmentId").value(a.getId().toString()));
        retire(home,0).andExpect(status().isConflict());
        a.cancel("Synthetic cancellation",Instant.now());assignments.flush();
        jdbc.update("insert into qualification_service_attention(id,organization_id,driver_id,assignment_id,kind,status,reason,created_at) values(?,?,?,?,'PENDING_REASSIGNMENT','OPEN','SYNTHETIC',now())",UUID.randomUUID(),org.getId(),driver.getId(),a.getId());
        read(home).andExpect(status().isOk()).andExpect(jsonPath("$.commitments[0].attentionKind").value("PENDING_REASSIGNMENT"));
        retire(home,0).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select active from transport_group where id=?",Boolean.class,home.getId())).isTrue();
    }
    @Test void groupCanBeRetiredWithoutCommitmentsAndItsHistoricalMembershipRemains()throws Exception{
        driver.activate();drivers.flush();
        retire(home,0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.active").value(false));
        read(home).andExpect(status().isOk()).andExpect(jsonPath("$.memberships[0].resourceId").value(driver.getId().toString()));
        change(home,command(1,"retired-group-key","VEHICLE",vehicle.getId(),true)).andExpect(status().isConflict());
    }
    @Test void addingAnOccupiedResourceDoesNotHideItsExistingCommitment()throws Exception{
        Assignment a=assignment();
        change(second,command(0,"occupied-resource-key","DRIVER",driver.getId(),true)).andExpect(status().isOk()).andExpect(jsonPath("$.commitments[0].assignmentId").value(a.getId().toString()));
        retire(second,1).andExpect(status().isConflict());
        change(second,command(1,"remove-occupied-key","DRIVER",driver.getId(),false)).andExpect(status().isConflict());
    }
    @Test void unrelatedDriverUpdatesDoNotReactivateAnExplicitlyRemovedMembership()throws Exception{
        change(home,command(0,"remove-home-key","DRIVER",driver.getId(),false)).andExpect(status().isOk());
        driver.activate();drivers.flush();
        assertThat(jdbc.queryForObject("select active from group_driver_membership where driver_id=? and group_id=?",Boolean.class,driver.getId(),home.getId())).isFalse();
    }
    private Assignment assignment(){Instant start=Instant.now().plusSeconds(3600);return assignments.saveAndFlush(Assignment.schedule(org,driver,vehicle,admin,"Synthetic start","Synthetic end",start,start.plusSeconds(3600),null,UUID.randomUUID().toString()));}
    private String path(TransportGroup g){return "/api/v1/groups/"+g.getId();}
    private ResultActions read(TransportGroup g)throws Exception{return mvc.perform(get(path(g)+"/resources").header("Authorization","Bearer "+token));}
    private ResultActions change(TransportGroup g,Map<String,Object> c)throws Exception{return mvc.perform(post(path(g)+"/memberships").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(c)));}
    private ResultActions retire(TransportGroup g,long v)throws Exception{return mvc.perform(patch(path(g)).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name",g.getName(),"active",false,"expectedVersion",v))));}
    private Map<String,Object> command(long v,String key,String kind,UUID resource,boolean active){return new LinkedHashMap<>(Map.of("expectedVersion",v,"operationKey",key,"reason","SYNTHETIC_REVIEW","resourceKind",kind,"resourceId",resource,"active",active));}
}
