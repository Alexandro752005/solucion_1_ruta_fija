package pe.rutafija.personidentity;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.fleet.domain.*;
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.support.NativePostgresIntegrationTest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit synthetic identities only. Gate ON; no mail, original files or actual owner's DNI. */
@SpringBootTest(properties={"debug=false","logging.level.root=INFO","logging.level.org.hibernate.SQL=INFO",
        "app.security.person-identity-gate.enabled=true","app.security.admin-terms-gate.enabled=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
class PersonIdentityFlowIT extends NativePostgresIntegrationTest {
    private static final String PASSWORD="Synthetic-Person-Password-2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired PasswordEncoder passwords;
    @Autowired DriverRepository drivers;
    @Autowired TransportGroupRepository groups;
    @MockitoSpyBean InvitationCodeHasher hasher;
    @MockitoBean OtpEmailSender mail;
    private AppUser owner,admin,conductor;
    private Organization organization,otherOrganization;

    @BeforeEach void prepare(){
        String suffix=UUID.randomUUID().toString();
        owner=AppUser.superAdmin("synthetic.owner."+suffix+"@rutafija.test",passwords.encode(PASSWORD),"Synthetic Owner");
        owner.verifyEmail(Instant.now());owner=users.saveAndFlush(owner);
        organization=organizations.save(Organization.active("Synthetic Person Organization "+suffix,"Synthetic Person","America/Lima"));
        otherOrganization=organizations.save(Organization.active("Other Synthetic Person Organization "+suffix,"Other Synthetic","America/Lima"));
        admin=account("admin",UserRole.ADMIN);
        conductor=account("conductor",UserRole.CONDUCTOR);
    }

    @Test void missingLegacyEvidenceDeniesExistingTokensAndOnlyExplicitOwnerBootstrapRegularizes() throws Exception {
        mvc.perform(get("/api/v1/platform/admins").with(auth(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/identity/status").with(auth(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityVerified").value(false)).andExpect(jsonPath("$.documentNumber").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").with(auth(owner))).andExpect(status().isOk());
        var invalid=enrollment("90000001");invalid.put("currentPassword","incorrect-password");
        mvc.perform(post("/api/v1/platform/identity/self-enrollment").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(invalid))).andExpect(status().isUnauthorized());
        invalid=enrollment("90000001");invalid.put("ownerDeclaration",false);
        mvc.perform(post("/api/v1/platform/identity/self-enrollment").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(invalid))).andExpect(status().isBadRequest());
        enrollOwner();
        mvc.perform(get("/api/v1/platform/admins").with(auth(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].identityVerified").value(false))
                .andExpect(jsonPath("$[0].documentNumber").doesNotExist())
                .andExpect(jsonPath("$[0].personHash").doesNotExist());
        assertThat(jdbc.queryForObject("select review_method from person_identity where user_id=?",String.class,owner.getId())).isEqualTo("OWNER_SELF_REVIEW");
        assertThat(count("select count(*) from person_identity where user_id=?",admin.getId())).isZero();
        assertThat(count("select count(*) from person_identity where user_id=?",conductor.getId())).isZero();
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name='person_identity'",String.class))
                .doesNotContain("document_number","full_name","email");
        verifyNoInteractions(mail);
    }

    @Test void onlyEnrolledOwnerMayReviewExistingAdminAndThisDoesNotOpenConductorDossiers() throws Exception {
        mvc.perform(get("/api/v1/groups").with(auth(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("40000001"))))
                .andExpect(status().isForbidden());
        enrollOwner();
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("40000001"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(true))
                .andExpect(jsonPath("$.documentNumber").doesNotExist()).andExpect(jsonPath("$.personHash").doesNotExist());
        mvc.perform(get("/api/v1/groups").with(auth(admin))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(admin))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("40000001"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/identity/self-enrollment").with(auth(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(enrollment("40000001")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",conductor.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("50000001"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/onboarding/requests/{id}/documentary-expediente",UUID.randomUUID()).with(auth(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/identity/status").with(auth(conductor))).andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(false));
        mvc.perform(get("/api/v1/mobile/profile").with(auth(conductor))).andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }

    @Test void commonDniCannotCreateAnotherRoleOrganizationOrAccountAndRollsBackHistoricalSlot() throws Exception {
        enrollOwner();
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(createInput(organization,"40000001")))).andExpect(status().isOk());
        int before=count("select count(*) from pilot_account_creation where role='ADMIN'");
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(createInput(otherOrganization,"40000001")))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(createInput(organization,"90000001")))).andExpect(status().isConflict());
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(before);
        assertThat(count("select count(*) from person_identity")).isEqualTo(2);
        assertThat(count("select count(*) from admin_activation")).isEqualTo(1);
        String hash=jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,owner.getId());
        assertThat(hash.trim()).isEqualTo(hasher.hashOrigin("conductor-person:DNI:90000001"));
        assertThat(hash).doesNotContain("90000001");
        var invalid=createInput(organization,"40000002");invalid.remove("identityReview");
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(invalid))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("40000001"))))
                .andExpect(status().isConflict());
        verifyNoInteractions(mail);
    }

    @Test void simultaneousCreationsWithSameReviewedDniProduceOneAccountAndOneHistoricalCreation() throws Exception {
        enrollOwner();int before=count("select count(*) from pilot_account_creation where role='ADMIN'");
        var first=createInput(organization,"40000003");var second=createInput(otherOrganization,"40000003");
        CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var one=executor.submit(()->{start.await();return mvc.perform(post("/api/v1/platform/admins").with(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(first))).andReturn().getResponse().getStatus();});
            var two=executor.submit(()->{start.await();return mvc.perform(post("/api/v1/platform/admins").with(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(second))).andReturn().getResponse().getStatus();});
            start.countDown();assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(before+1);
        assertThat(count("select count(*) from person_identity where person_hash=?",hasher.hashOrigin("conductor-person:DNI:40000003"))).isEqualTo(1);
        assertThat(count("select count(*) from admin_activation")).isEqualTo(1);
    }

    @Test void fingerprintDriftBlocksNewBindingsAndAReviewedIdentityCannotBeSubstituted() throws Exception {
        enrollOwner();
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("40000001")))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("40000002")))).andExpect(status().isConflict());
        doReturn("0".repeat(64)).when(hasher).hashOrigin("person-identity:key-fingerprint:v1");
        int before=count("select count(*) from pilot_account_creation where role='ADMIN'");
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(createInput(organization,"40000003")))).andExpect(status().isConflict());
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(before);
        assertThat(count("select count(*) from person_identity")).isEqualTo(2);
        verifyNoInteractions(mail);
    }

    @Test void aLegacyDriverMatchSignalsConflictWithoutImplicitAdoptionIntoAnotherRole() throws Exception {
        Driver legacy=legacyDriver("50000001");enrollOwner();
        int before=count("select count(*) from pilot_account_creation where role='ADMIN'");
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(createInput(organization,"50000001")))).andExpect(status().isConflict());
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(before);
        assertThat(count("select count(*) from person_identity where user_id=?",conductor.getId())).isZero();
        assertThat(drivers.findById(legacy.getId()).orElseThrow().isActive()).isTrue();
        assertThat(count("select count(*) from admin_activation")).isZero();
    }

    @Test void manualLegacyReviewPreservesAccountDriverAndQuotaAndNeverEnablesOperation() throws Exception {
        Driver legacy=legacyDriver("50000001");enrollOwner();reviewAdmin(admin,"40000001");
        AppUser other=AppUser.organizationUser(otherOrganization,"synthetic.other."+UUID.randomUUID()+"@rutafija.test",
                passwords.encode(PASSWORD),"Synthetic Other ADMIN",UserRole.ADMIN);other.verifyEmail(Instant.now());other=users.saveAndFlush(other);
        reviewAdmin(other,"40000002");
        int before=count("select count(*) from pilot_account_creation");
        mvc.perform(post("/api/v1/drivers/{id}/identity-review",legacy.getId()).with(auth(other))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("50000001")))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/drivers/{id}/identity-review",legacy.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("50000001")))).andExpect(status().isForbidden());
        for(int attempt=0;attempt<2;attempt++)mvc.perform(post("/api/v1/drivers/{id}/identity-review",legacy.getId()).with(auth(admin))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("50000001"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(conductor.getId().toString()))
                .andExpect(jsonPath("$.identityVerified").value(true)).andExpect(jsonPath("$.documentNumber").doesNotExist());
        Driver stored=drivers.findById(legacy.getId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();assertThat(stored.getDocumentNumber()).isEqualTo("50000001");
        assertThat(count("select count(*) from pilot_account_creation")).isEqualTo(before);
        assertThat(count("select count(*) from app_user where id=?",conductor.getId())).isEqualTo(1);
        assertThat(count("select count(*) from driver where id=? and user_id=?",legacy.getId(),conductor.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select session_version from app_user where id=?",Long.class,conductor.getId())).isEqualTo(1L);
        mvc.perform(get("/api/v1/identity/status").with(auth(conductor))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/mobile/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("email",conductor.getEmail(),"password",PASSWORD))))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from conductor_onboarding_request")).isZero();
        verifyNoInteractions(mail);
    }

    @Test void discrepancyWithLegacyDriverIsRecordedWithoutSilentlyReplacingItsStoredIdentity() throws Exception {
        Driver legacy=legacyDriver("50000001");enrollOwner();reviewAdmin(admin,"40000001");
        mvc.perform(post("/api/v1/drivers/{id}/identity-review",legacy.getId()).with(auth(admin))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand("50000002"))))
                .andExpect(status().isOk());
        assertThat(drivers.findById(legacy.getId()).orElseThrow().getDocumentNumber()).isEqualTo("50000001");
        assertThat(jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,conductor.getId()).trim())
                .isEqualTo(hasher.hashOrigin("conductor-person:DNI:50000002"));
        String metadata=jdbc.queryForObject("select metadata_json::text from audit_event where action='CONDUCTOR_IDENTITY_REVIEWED' and entity_id=?",String.class,legacy.getId());
        assertThat(mapper.readTree(metadata).path("storedIdentityMatches").asBoolean()).isFalse();
        assertThat(mapper.readTree(metadata).path("documentaryApprovalGranted").asBoolean()).isFalse();
        assertThat(metadata).doesNotContain("50000001","50000002",conductor.getEmail());
        assertThat(drivers.findById(legacy.getId()).orElseThrow().isActive()).isFalse();
        assertThat(count("select count(*) from conductor_onboarding_request")).isZero();
        verifyNoInteractions(mail);
    }

    @Test void concurrentAdminsCanReviewTheSameLegacyConductorWithoutUpgradingOrganizationLocks() throws Exception {
        Driver legacy=legacyDriver("50000001");enrollOwner();reviewAdmin(admin,"40000001");
        AppUser colleague=account("colleague",UserRole.ADMIN);reviewAdmin(colleague,"40000002");
        CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{start.await();return mvc.perform(post("/api/v1/drivers/{id}/identity-review",legacy.getId())
                    .with(auth(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(reviewCommand("50000001")))).andReturn().getResponse().getStatus();});
            var second=executor.submit(()->{start.await();return mvc.perform(post("/api/v1/drivers/{id}/identity-review",legacy.getId())
                    .with(auth(colleague)).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(reviewCommand("50000001")))).andReturn().getResponse().getStatus();});
            start.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(20,TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(count("select count(*) from person_identity where user_id=?",conductor.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select session_version from app_user where id=?",Long.class,conductor.getId())).isEqualTo(1L);
        assertThat(drivers.findById(legacy.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test void invalidManualReviewInputsNeverReserveIdentityAndFiveHundredCharacterReasonIsAccepted() throws Exception {
        enrollOwner();
        for(var invalid:List.of(Map.<String,Object>of("documentNumber","1234567"),
                Map.<String,Object>of("documentType","CE"),Map.<String,Object>of("method","EMAIL_ONLY"),
                Map.<String,Object>of("originalReviewed",false),Map.<String,Object>of("identityConfirmed",false),
                Map.<String,Object>of("reference","SYNTHETIC includes 12345678"))){
            Map<String,Object> candidate=new HashMap<>(identityReview("40000001"));candidate.putAll(invalid);
            mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(owner))
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(
                            Map.of("identityReview",candidate,"reason","SYNTHETIC negative input"))))
                    .andExpect(status().isBadRequest());
            assertThat(count("select count(*) from person_identity where user_id=?",admin.getId())).isZero();
        }
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",admin.getId()).with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(
                        Map.of("identityReview",identityReview("40000001"),"reason","R".repeat(500)))))
                .andExpect(status().isOk());
    }

    private AppUser account(String prefix,UserRole role){AppUser user=AppUser.organizationUser(organization,
            "synthetic."+prefix+"."+UUID.randomUUID()+"@rutafija.test",passwords.encode(PASSWORD),"Synthetic "+role,role);
        user.verifyEmail(Instant.now());return users.saveAndFlush(user);}
    private Driver legacyDriver(String dni){var group=groups.save(TransportGroup.active(organization,"Synthetic Legacy Group",null));
        return drivers.saveAndFlush(Driver.register(organization,conductor,group,"Synthetic Legacy Conductor",null,"DNI",dni,"SYNTHETIC-LICENSE"));}
    private void reviewAdmin(AppUser target,String dni)throws Exception{mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",target.getId()).with(auth(owner))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reviewCommand(dni))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(true));}
    private Map<String,Object> identityReview(String dni){return Map.of("documentType","DNI","documentNumber",dni,
            "method","IN_PERSON","reference","SYNTHETIC visual inspection","originalReviewed",true,"identityConfirmed",true);}
    private Map<String,Object> enrollment(String dni){return new HashMap<>(Map.of("currentPassword",PASSWORD,"ownerDeclaration",true,"identityReview",identityReview(dni)));}
    private Map<String,Object> reviewCommand(String dni){return Map.of("identityReview",identityReview(dni),"reason","SYNTHETIC authorized review");}
    private Map<String,Object> createInput(Organization org,String dni){return new HashMap<>(Map.of("organizationId",org.getId(),
            "email","synthetic.candidate."+UUID.randomUUID()+"@rutafija.test","fullName","Synthetic Candidate ADMIN",
            "reason","SYNTHETIC pilot creation","identityReview",identityReview(dni)));}
    private void enrollOwner()throws Exception{mvc.perform(post("/api/v1/platform/identity/self-enrollment").with(auth(owner))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(enrollment("90000001"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(true));}
    private RequestPostProcessor auth(AppUser user){return jwt().authorities(new SimpleGrantedAuthority("ROLE_"+user.getRole().name()))
            .jwt(value->{value.subject(user.getId().toString()).claim("role",user.getRole().name()).claim("sessionVersion",user.getSessionVersion());
                if(user.getOrganizationId()!=null)value.claim("organizationId",user.getOrganizationId().toString());});}
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
}
