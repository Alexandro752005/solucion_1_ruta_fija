package pe.rutafija.onboarding.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.application.EmailOtpService;
import pe.rutafija.identity.application.OpaqueTokenService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.onboarding.application.ConductorDocumentaryCatalog;
import pe.rutafija.onboarding.infrastructure.ConductorDocumentaryRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;
import pe.rutafija.shared.exception.ApplicationException;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** All documents and titles are synthetic. These tests assert workflow, never external habilitation. */
@SpringBootTest(properties={"debug=false","logging.level.root=INFO","logging.level.org.hibernate.SQL=INFO"})
@AutoConfigureMockMvc @ActiveProfiles("test")
@Import(ConductorDocumentaryFlowIT.ClockConfiguration.class)
class ConductorDocumentaryFlowIT extends NativePostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired TransportGroupRepository groups;
    @Autowired InvitationCodeHasher hasher;
    @Autowired DocumentaryClock clock;
    @Autowired EmailOtpService otpService;
    @Autowired OpaqueTokenService opaqueTokens;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean ConductorDocumentaryRepository documentaryRepository;
    @MockitoBean OtpEmailSender email;
    private static final String PASSWORD="Synthetic-Documentary-Password-2026";
    private UUID identityId,requestId;
    private Organization organization,otherOrganization;
    private AppUser admin,otherAdmin;
    private String applicantEmail,token;
    private Instant initial;

    @BeforeEach void prepare()throws Exception {
        initial=Instant.now().truncatedTo(ChronoUnit.MICROS);clock.set(initial);
        String suffix=UUID.randomUUID().toString().substring(0,8);
        organization=organizations.save(Organization.active("Synthetic Documentary "+suffix,"Synthetic "+suffix,"America/Lima"));
        otherOrganization=organizations.save(Organization.active("Other Synthetic Documentary "+suffix,"Other Synthetic "+suffix,"America/Lima"));
        admin=admin(organization,"admin."+suffix+"@rutafija.test");
        otherAdmin=admin(otherOrganization,"other.admin."+suffix+"@rutafija.test");
        identityId=UUID.randomUUID();requestId=UUID.randomUUID();applicantEmail="synthetic.documentary."+suffix+"@rutafija.test";
        jdbc.update("""
                insert into conductor_onboarding_identity(id,email,password_hash,full_name,phone,email_verified_at,created_at,updated_at)
                values(?,?,?,?,?,?,?,?)
                """,identityId,applicantEmail,passwords.encode(PASSWORD),"Synthetic Documentary Applicant","999999999",ts(initial),ts(initial),ts(initial));
        UUID invitation=invitation("123456",true);
        jdbc.update("""
                insert into conductor_onboarding_request(id,identity_id,organization_id,invitation_id,idempotency_key,submitted_at)
                values(?,?,?,?,?,?)
                """,requestId,identityId,organization.getId(),invitation,"fixture-"+UUID.randomUUID(),ts(initial));
        token=login();
    }

    @Test void observationsAndHistoryCannotRepeatTheReviewedNameAcrossRequirements() throws Exception {
        String reviewedName="Synthetic Reviewed Person Alpha";
        var documents=new ArrayList<>(reviewedDocuments());
        Map<String,Object> identity=new HashMap<>(documents.get(0));
        identity.put("fields",Map.of("documentType","DNI","documentNumber","12345678","fullName",reviewedName,"issuer","SYNTHETIC"));
        documents.set(0,identity);
        Map<String,Object> license=new HashMap<>(documents.get(1));
        license.put("observation","Revisar la licencia de "+reviewedName);
        documents.set(1,license);
        saveAdmin(0,documents).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from conductor_document_revision where request_id=?",requestId)).isZero();
        license.remove("observation");
        saveAdmin(0,documents).andExpect(status().isOk());
        adminAction("return",decision(1,"Corregir los datos de "+reviewedName)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select status from conductor_onboarding_request where id=?",String.class,requestId)).isEqualTo("PENDING_REVIEW");
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",token))
                .andExpect(status().isOk()).andExpect(result->assertThat(result.getResponse().getContentAsString()).doesNotContain(reviewedName));
    }

    @Test void nullDocumentIsRejectedWithoutChangingTheDossier() throws Exception {
        mvc.perform(put(adminPath("documentary-expediente")).with(auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"operationKey":"synthetic-null-document",
                                 "modality":"TAXI","documents":[null]}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select version from conductor_onboarding_request where id=?",
                Long.class, requestId)).isZero();
    }

    @Test void provisioningCarriesRollingThreeSendBudgetAndFailedAttemptsWithoutLiveOtpOrToken() throws Exception {
        seedOtpHistory(1,null,initial,initial.plusSeconds(5),initial.plusSeconds(10));
        clock.set(initial.plusSeconds(15));
        UUID userId=provisionSyntheticApplicant();
        Map<String,Object> transferred=jdbc.queryForMap("""
                select verification_token_hash,otp_hash,otp_issued_at,otp_expires_at,challenge_expires_at,
                       failed_attempts,blocked_until,send_at_1,send_at_2,send_at_3,consumed_at
                  from email_otp_challenge where user_id=?
                """,userId);
        for(String key:List.of("verification_token_hash","otp_hash","otp_issued_at","otp_expires_at","challenge_expires_at"))
            assertThat(transferred.get(key)).as(key).isNull();
        assertThat(((Number)transferred.get("failed_attempts")).intValue()).isEqualTo(1);
        assertThat(transferred.get("consumed_at")).isNotNull();
        assertThat(((Timestamp)transferred.get("send_at_1")).toInstant()).isEqualTo(initial);
        assertThat(((Timestamp)transferred.get("send_at_3")).toInstant()).isEqualTo(initial.plusSeconds(10));
        assertThat(count("select count(*) from conductor_onboarding_email_challenge where identity_id=?",identityId)).isEqualTo(1);
        AppUser account=users.findById(userId).orElseThrow();
        clock.set(initial.plus(Duration.ofHours(1)).minusNanos(1000));
        assertThatThrownBy(()->otpService.beginForConductorRecovery(account,"synthetic.recovery@rutafija.test",UUID.randomUUID()))
                .isInstanceOfSatisfying(ApplicationException.class,exception->assertThat(exception.getCode().name()).isEqualTo("EMAIL_OTP_RATE_LIMITED"));
        verifyNoInteractions(email);
        clock.set(initial.plus(Duration.ofHours(1)));
        var ticket=otpService.beginForConductorRecovery(account,"synthetic.recovery@rutafija.test",UUID.randomUUID());
        assertThat(ticket.emailSent()).isTrue();
        verify(email).sendVerificationCode(eq("synthetic.recovery@rutafija.test"),any(String.class),eq(clock.instant().plus(Duration.ofMinutes(5))));
        assertThatThrownBy(()->otpService.beginForConductorRecovery(account,"synthetic.another@rutafija.test",UUID.randomUUID()))
                .isInstanceOfSatisfying(ApplicationException.class,exception->assertThat(exception.getCode().name()).isEqualTo("EMAIL_OTP_RATE_LIMITED"));
    }

    @Test void provisioningRetiresTheConsumedPreliminaryVerificationProofWithoutLosingHistory() throws Exception {
        seedOtpHistory(1,null,initial,null,null);
        byte[] previousProof=mapper.writeValueAsBytes(Map.of("verificationToken","SYNTHETIC-consumed-history","code","123456"));
        mvc.perform(post("/api/v1/onboarding/otp/verify").contentType(MediaType.APPLICATION_JSON).content(previousProof))
                .andExpect(status().isAccepted());
        UUID accountId=provisionSyntheticApplicant();
        mvc.perform(post("/api/v1/onboarding/otp/verify").contentType(MediaType.APPLICATION_JSON).content(previousProof))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));
        Map<String,Object> preserved=jdbc.queryForMap("""
                select verification_token_hash,failed_attempts,send_at_1,consumed_at,request_id
                  from conductor_onboarding_email_challenge where identity_id=?
                """,identityId);
        assertThat(preserved.get("verification_token_hash").toString().trim()).isNotEqualTo(opaqueTokens.hash("SYNTHETIC-consumed-history"));
        assertThat(((Number)preserved.get("failed_attempts")).intValue()).isEqualTo(1);
        assertThat(((Timestamp)preserved.get("send_at_1")).toInstant()).isEqualTo(initial);
        assertThat(preserved.get("consumed_at")).isNotNull();
        assertThat(preserved.get("request_id")).isEqualTo(requestId);
        assertThat(count("select count(*) from email_otp_challenge where user_id=? and verification_token_hash is null and otp_hash is null",accountId)).isEqualTo(1);
        verifyNoInteractions(email);
    }

    @ParameterizedTest
    @ValueSource(strings={"documents","resubmit","reapply"})
    void applicantMutationWaitingForIdentityLockCannotWriteAfterConfirmedLogout(String action) throws Exception {
        long version=0;
        if("resubmit".equals(action)) {
            adminAction("return",decision(0,"SYNTHETIC correction required")).andExpect(status().isOk());version=1;
        } else if("reapply".equals(action)) {
            adminAction("reject",decision(0,"SYNTHETIC express rejection")).andExpect(status().isOk());
            invitation("654321",false);version=1;
        }
        MockHttpServletRequestBuilder mutation;
        if("documents".equals(action)) {
            var pending=document("CON-01",Map.of("documentType","DNI","documentNumber","12345678",
                    "fullName","Synthetic Applicant","issuer","SYNTHETIC"),"PENDING");
            pending.put("reviewReference",null);pending.put("observation",null);
            mutation=put(applicantPath("documents")).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(saveCommand(version,List.of(pending))));
        } else if("resubmit".equals(action)) {
            mutation=post(applicantPath("resubmit")).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(decision(version,"SYNTHETIC correction submitted")));
        } else {
            mutation=post(applicantPath("reapply")).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(Map.of("expectedVersion",version,"operationKey",UUID.randomUUID().toString(),"invitationCode","654321")));
        }
        mutation.header("X-Onboarding-Token",token);
        int priorEvents=count("select count(*) from conductor_onboarding_review_event where request_id=?",requestId);
        CountDownLatch reachedLock=new CountDownLatch(1);
        doAnswer(invocation->{reachedLock.countDown();return invocation.callRealMethod();})
                .when(documentaryRepository).lock(eq(requestId),isNull(),eq(identityId));
        AtomicReference<Future<Integer>> waitingMutation=new AtomicReference<>();
        try(var executor=Executors.newFixedThreadPool(2)) {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction->{
                jdbc.queryForObject("select id from conductor_onboarding_identity where id=? for update",UUID.class,identityId);
                waitingMutation.set(executor.submit(()->mvc.perform(mutation).andReturn().getResponse().getStatus()));
                try {
                    assertThat(reachedLock.await(10,TimeUnit.SECONDS)).isTrue();
                    assertThat(executor.submit(()->mvc.perform(post("/api/v1/onboarding/access/logout")
                            .header("X-Onboarding-Token",token)).andReturn().getResponse().getStatus()).get(10,TimeUnit.SECONDS)).isEqualTo(204);
                    assertThat(waitingMutation.get().isDone()).isFalse();
                } catch(Exception exception) {throw new IllegalStateException("Synthetic concurrent logout failed",exception);}
            });
            assertThat(waitingMutation.get().get(10,TimeUnit.SECONDS)).isEqualTo(401);
        }
        assertThat(count("select count(*) from conductor_document_revision where request_id=?",requestId)).isZero();
        assertThat(count("select count(*) from conductor_onboarding_review_event where request_id=?",requestId)).isEqualTo(priorEvents);
        assertThat(jdbc.queryForObject("select version from conductor_onboarding_request where id=?",Long.class,requestId)).isEqualTo(version);
        assertThat(count("select count(*) from conductor_onboarding_request where identity_id=?",identityId)).isEqualTo(1);
        verifyNoInteractions(email);
    }

    @Test void provisioningPreservesVerificationBlockUntilItsExactThirtyMinuteBoundary() throws Exception {
        seedOtpHistory(2,initial.plus(Duration.ofMinutes(30)),initial,null,null);
        UUID userId=provisionSyntheticApplicant();
        AppUser account=users.findById(userId).orElseThrow();
        clock.set(initial.plus(Duration.ofMinutes(30)).minusNanos(1000));
        assertThatThrownBy(()->otpService.beginForConductorRecovery(account,"synthetic.block@rutafija.test",UUID.randomUUID()))
                .isInstanceOfSatisfying(ApplicationException.class,exception->assertThat(exception.getCode().name()).isEqualTo("EMAIL_OTP_LOCKED"));
        verifyNoInteractions(email);
        clock.set(initial.plus(Duration.ofMinutes(30)));
        assertThat(otpService.beginForConductorRecovery(account,"synthetic.block@rutafija.test",UUID.randomUUID()).emailSent()).isTrue();
        assertThat(jdbc.queryForObject("select failed_attempts from email_otp_challenge where user_id=?",Integer.class,userId)).isZero();
        assertThat(jdbc.queryForObject("select blocked_until from email_otp_challenge where user_id=?",Timestamp.class,userId)).isNull();
    }

    @Test void linkedScopedAccessUsesCurrentRecoveredAccountAndNeverRevivesAfterDeactivation() throws Exception {
        String preliminaryToken=token;
        UUID userId=provisionSyntheticApplicant();
        String originalPersonHash=jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,userId);
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",preliminaryToken)).andExpect(status().isUnauthorized());
        String linkedToken=login();
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",linkedToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false));
        var snapshot=jdbc.queryForMap("select linked_user_id,linked_session_version from conductor_onboarding_access_token where token_hash=?",opaqueTokens.hash(linkedToken));
        assertThat(snapshot.get("linked_user_id")).isEqualTo(userId);
        assertThat(((Number)snapshot.get("linked_session_version")).longValue()).isZero();
        String recoveredEmail="synthetic.recovered."+UUID.randomUUID()+"@rutafija.test";
        JsonNode started=node(mvc.perform(post("/api/v1/users/{id}/conductor-recovery",userId).with(auth(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of(
                                "newEmail",recoveredEmail,"evidenceMethod","IN_PERSON","evidenceReference","SYNTHETIC independent check",
                                "reason","SYNTHETIC continuity recovery","expedienteReviewed",true,"independentEvidenceConfirmed",true,"passwordResetRequired",true))))
                .andExpect(status().isAccepted()).andReturn());
        ArgumentCaptor<String> code=ArgumentCaptor.forClass(String.class);
        verify(email).sendVerificationCode(eq(recoveredEmail),code.capture(),any(Instant.class));
        JsonNode verified=node(mvc.perform(post("/api/v1/auth/conductor-recovery/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of(
                                "recoveryId",started.path("recoveryId").asText(),"verificationToken",started.path("verificationToken").asText(),"code",code.getValue()))))
                .andExpect(status().isOk()).andReturn());
        String newPassword="Synthetic-New-Password-2026";
        mvc.perform(post("/api/v1/auth/conductor-recovery/{id}/password-reset",started.path("recoveryId").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of(
                                "passwordResetToken",verified.path("passwordResetToken").asText(),"newPassword",newPassword))))
                .andExpect(status().isOk());
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",linkedToken)).andExpect(status().isUnauthorized());
        for(var credentials:List.of(Map.of("email",applicantEmail,"password",PASSWORD),Map.of("email",applicantEmail,"password",newPassword),Map.of("email",recoveredEmail,"password",PASSWORD)))
            mvc.perform(post("/api/v1/onboarding/access/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(credentials))).andExpect(status().isUnauthorized());
        String currentToken=node(mvc.perform(post("/api/v1/onboarding/access/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email",recoveredEmail,"password",newPassword))))
                .andExpect(status().isOk()).andReturn()).path("accessToken").asText();
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",currentToken)).andExpect(status().isOk());
        AppUser account=users.findById(userId).orElseThrow();account.deactivate();users.saveAndFlush(account);
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",currentToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/onboarding/access/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("email",recoveredEmail,"password",newPassword)))).andExpect(status().isUnauthorized());
        account=users.findById(userId).orElseThrow();account.activate();users.saveAndFlush(account);
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",currentToken)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select email from conductor_onboarding_identity where id=?",String.class,identityId)).isEqualTo(applicantEmail);
        assertThat(count("select count(*) from app_user where role='CONDUCTOR'")).isEqualTo(1);
        assertThat(count("select count(*) from pilot_account_creation where role='CONDUCTOR'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select person_hash from person_identity where user_id=?",String.class,userId)).isEqualTo(originalPersonHash);
        assertThat(count("select count(*) from conductor_common_person_bridge")).isEqualTo(1);
    }

    @Test void conductorProvisionCannotReuseTheReviewedPersonAlreadyBoundToAnAdminRole() throws Exception {
        AppUser owner=AppUser.superAdmin("synthetic.owner."+UUID.randomUUID()+"@rutafija.test",passwords.encode(PASSWORD),"Synthetic Owner");
        owner.verifyEmail(initial);owner=users.saveAndFlush(owner);
        Map<String,Object> ownerReview=Map.of("documentType","DNI","documentNumber","90000001","method","IN_PERSON",
                "reference","SYNTHETIC visual inspection","originalReviewed",true,"identityConfirmed",true);
        mvc.perform(post("/api/v1/platform/identity/self-enrollment").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("currentPassword",PASSWORD,"ownerDeclaration",true,"identityReview",ownerReview))))
                .andExpect(status().isOk());
        Map<String,Object> adminReview=Map.of("documentType","DNI","documentNumber","12345678","method","IN_PERSON",
                "reference","SYNTHETIC visual inspection","originalReviewed",true,"identityConfirmed",true);
        mvc.perform(post("/api/v1/platform/admins/{id}/identity-review",otherAdmin.getId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("identityReview",adminReview,"reason","SYNTHETIC restricted review"))))
                .andExpect(status().isOk());
        saveAdmin(0,reviewedDocuments()).andExpect(status().isOk());
        adminAction("approve",decision(1,"SYNTHETIC personal review")).andExpect(status().isOk());
        var group=groups.save(TransportGroup.active(organization,"SYNTHETIC role-conflict group",null));
        var provision=decision(2,"SYNTHETIC duplicate role attempt");provision.put("groupId",group.getId().toString());
        adminAction("provision",provision).andExpect(status().isConflict());
        assertThat(count("select count(*) from app_user where role='CONDUCTOR'")).isZero();
        assertThat(count("select count(*) from driver")).isZero();
        assertThat(count("select count(*) from pilot_account_creation where role='CONDUCTOR'")).isZero();
        assertThat(count("select count(*) from conductor_person_binding")).isZero();
        assertThat(count("select count(*) from person_identity where user_id=?",otherAdmin.getId())).isEqualTo(1);
        verifyNoInteractions(email);
    }

    private UUID provisionSyntheticApplicant() throws Exception {
        saveAdmin(0,reviewedDocuments()).andExpect(status().isOk());
        adminAction("approve",decision(1,"SYNTHETIC personal review")).andExpect(status().isOk());
        var group=groups.save(TransportGroup.active(organization,"SYNTHETIC continuity group",null));
        var command=decision(2,"SYNTHETIC account continuity provision");command.put("groupId",group.getId().toString());
        return UUID.fromString(adminAction("provision",command).andExpect(status().isOk()).andReturnNode().path("provisionedUserId").asText());
    }

    private void seedOtpHistory(int failures,Instant blockedUntil,Instant first,Instant second,Instant third) {
        UUID invitationId=jdbc.queryForObject("select invitation_id from conductor_onboarding_request where id=?",UUID.class,requestId);
        jdbc.update("""
                insert into conductor_onboarding_email_challenge
                    (id,identity_id,invitation_id,idempotency_key,verification_token_hash,challenge_expires_at,
                     failed_attempts,blocked_until,send_at_1,send_at_2,send_at_3,consumed_at,created_at,updated_at,request_id)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),identityId,invitationId,"SYNTHETIC-history",opaqueTokens.hash("SYNTHETIC-consumed-history"),
                ts(initial.plus(Duration.ofMinutes(30))),failures,blockedUntil==null?null:ts(blockedUntil),
                first==null?null:ts(first),second==null?null:ts(second),third==null?null:ts(third),ts(initial),ts(initial),ts(initial),requestId);
    }

    @Test void provisioningUsesTheReviewedNameAndPreservesTheOriginalDeclaration() throws Exception {
        var reviewed = reviewedDocuments();
        reviewed.getFirst().put("fields", Map.of("documentType", "DNI", "documentNumber", "12345678",
                "fullName", "Synthetic Corrected Applicant", "issuer", "SYNTHETIC"));
        saveAdmin(0, reviewed).andExpect(status().isOk());
        adminAction("approve", decision(1, "Correccion documental sintetica revisada"))
                .andExpect(status().isOk());
        var group = groups.save(TransportGroup.active(organization, "Synthetic correction group", null));
        var command = decision(2, "Provision del nombre documental revisado");
        command.put("groupId", group.getId().toString());
        var result = adminAction("provision", command).andExpect(status().isOk()).andReturnNode();
        UUID userId = UUID.fromString(result.path("provisionedUserId").asText());
        assertThat(jdbc.queryForObject("select full_name from app_user where id=?", String.class, userId))
                .isEqualTo("Synthetic Corrected Applicant");
        assertThat(jdbc.queryForObject("select full_name from driver where user_id=?", String.class, userId))
                .isEqualTo("Synthetic Corrected Applicant");
        assertThat(jdbc.queryForObject("select full_name from conductor_onboarding_identity where id=?",
                String.class, identityId)).isEqualTo("Synthetic Documentary Applicant");
    }

    @Test void personalApprovalThenProvisionIsSeparateFromGroupAndOperationalEligibility()throws Exception {
        JsonNode reviewed=saveAdmin(0,reviewedDocuments()).andExpect(status().isOk()).andReturnNode();
        assertThat(reviewed.path("catalogId").asText()).isEqualTo("L01-CHICLAYO");
        assertThat(reviewed.path("catalogHash").asText()).isEqualTo(ConductorDocumentaryCatalog.HASH);
        JsonNode approved=adminAction("approve",decision(1,"Revision visual sintetica conforme"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personalApproved").value(true))
                .andExpect(jsonPath("$.operationalEnabled").value(false)).andReturnNode();
        assertThat(approved.hasNonNull("provisionedUserId")).isFalse();
        mvc.perform(get(adminPath("expediente")).with(auth(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(count("select count(*) from app_user where email=?",applicantEmail)).isZero();
        adminAction("provision",decision(2,"Asignacion posterior de grupo"))
                .andExpect(status().isConflict());
        TransportGroup group=groups.save(TransportGroup.active(organization,"Synthetic approved group",null));
        Map<String,Object> command=decision(2,"Provision documental sintetica");command.put("groupId",group.getId().toString());
        JsonNode provisioned=adminAction("provision",command).andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalEnabled").value(false)).andReturnNode();
        UUID userId=UUID.fromString(provisioned.path("provisionedUserId").asText());
        UUID driverId=UUID.fromString(provisioned.path("provisionedDriverId").asText());
        assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,driverId)).isFalse();
        assertThat(jdbc.queryForObject("select organization_id from app_user where id=?",UUID.class,userId)).isEqualTo(organization.getId());
        assertThat(count("select count(*) from pilot_account_creation where user_id=?",userId)).isEqualTo(1);
        adminAction("provision",command).andExpect(status().isOk())
                .andExpect(jsonPath("$.provisionedUserId").value(userId.toString()));
        assertThat(count("select count(*) from app_user where email=?",applicantEmail)).isEqualTo(1);
        mvc.perform(post("/api/v1/mobile/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email",applicantEmail,"password",PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DRIVER_INACTIVE"));
        verifyNoInteractions(email);
    }

    @Test void unresolvedRequirementsNoApplyAndExpiredOrIncompatibleLicensesCannotBeApproved()throws Exception {
        adminAction("approve",decision(0,"Intento sin expediente" )).andExpect(status().isConflict());
        List<Map<String,Object>> unknown=reviewedDocuments();unknown.get(2).put("applicability","POR_CONTRASTAR");
        saveAdmin(0,unknown).andExpect(status().isConflict());
        List<Map<String,Object>> bypass=reviewedDocuments();bypass.get(0).put("result","NO_APLICA_JUSTIFICADO");
        saveAdmin(0,bypass).andExpect(status().isConflict());
        List<Map<String,Object>> expired=reviewedDocuments();expired.get(1).put("validUntil",initial.minusSeconds(1).toString());
        saveAdmin(0,expired).andExpect(status().isConflict());
        List<Map<String,Object>> incompatible=reviewedDocuments();incompatible.get(1).put("fields",Map.of("licenseNumber","SYN-LICENSE","licenseClass","A","licenseCategory","A-I","restrictions","NINGUNA","issuer","SYNTHETIC"));
        saveAdmin(0,incompatible).andExpect(status().isConflict());
        List<Map<String,Object>> unknownExpiry=reviewedDocuments();unknownExpiry.get(0).put("expiryMode","UNKNOWN");
        saveAdmin(0,unknownExpiry).andExpect(status().isConflict());
        List<Map<String,Object>> undatedHabilitation=reviewedDocuments();
        undatedHabilitation.get(2).put("result","CONFORME");
        undatedHabilitation.get(2).put("fields",Map.of("titleNumber","SYN-TITLE","authority","SYNTHETIC",
                "serviceScope","SYNTHETIC TAXI","operatorReference","SYN-OPERATOR"));
        saveAdmin(0,undatedHabilitation).andExpect(status().isConflict());
        List<Map<String,Object>> unexpected=reviewedDocuments();unexpected.get(0).put("fields",Map.of("attachment","base64-not-allowed"));
        saveAdmin(0,unexpected).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from conductor_document_revision")).isZero();
        assertThat(jdbc.queryForObject("select status from conductor_onboarding_request where id=?",String.class,requestId)).isEqualTo("PENDING_REVIEW");
    }

    @Test void returnsAreIdempotentCorrectionDoesNotAddRoundAndDeadlineDoesNotRejectAutomatically()throws Exception {
        List<Map<String,Object>> observed=reviewedDocuments();observed.get(1).put("result","OBSERVADO");observed.get(1).put("observation","Corregir la categoria y volver a presentar");
        saveAdmin(0,observed).andExpect(status().isOk());
        Map<String,Object> returned=decision(1,"Revisar los requisitos observados");
        JsonNode result=adminAction("return",returned).andExpect(status().isOk()).andExpect(jsonPath("$.reviewRounds").value(1)).andReturnNode();
        assertThat(Instant.parse(result.path("correctionDeadline").asText())).isEqualTo(initial.plus(Duration.ofHours(48)));
        adminAction("return",returned).andExpect(status().isOk()).andExpect(jsonPath("$.reviewRounds").value(1));
        Map<String,Object> changed=new HashMap<>(returned);changed.put("reason","Otro contenido");
        adminAction("return",changed).andExpect(status().isConflict());
        clock.set(initial.plus(Duration.ofHours(49)));token=login();
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.documents[0].fields").isEmpty());
        Map<String,Object> correction=new HashMap<>(reviewedDocuments().get(1));
        correction.put("result","PENDING");correction.put("reviewReference",null);correction.put("observation",null);
        mvc.perform(put(applicantPath("documents")).header("X-Onboarding-Token",token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(saveCommand(2,List.of(correction)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewRounds").value(1));
        mvc.perform(post(applicantPath("resubmit")).header("X-Onboarding-Token",token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(decision(3,"Correcciones presentadas"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.reviewRounds").value(1));
        adminAction("approve",decision(4,"Revision aun pendiente")).andExpect(status().isConflict());
        assertThat(count("select count(*) from conductor_document_revision where request_id=?",requestId)).isEqualTo(6);
        assertThatThrownBy(()->jdbc.update("update conductor_document_revision set observation='mutated' where request_id=?",requestId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from conductor_onboarding_review_event where request_id=?",requestId)).isInstanceOf(DataAccessException.class);
    }

    @Test void fourthReturnEnablesExpressReiterationRejectionAndSevenDayWaitWithSameIdentity()throws Exception {
        long version=0;
        for(int round=1;round<=4;round++) {
            adminAction("return",decision(version++,"Faltan requisitos para revisar")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewRounds").value(round));
            if(round<4) {
                Map<String,Object> early=decision(version,"Rechazo reiteracion no permitido");early.put("reiteration",true);
                adminAction("reject",early).andExpect(status().isConflict());
            }
            mvc.perform(post(applicantPath("resubmit")).header("X-Onboarding-Token",token)
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(decision(version++,"Presentacion para nueva revision"))))
                    .andExpect(status().isOk());
        }
        Map<String,Object> reject=decision(version++,"Rechazo expreso por reiteracion");reject.put("reiteration",true);
        adminAction("reject",reject).andExpect(status().isOk()).andExpect(jsonPath("$.reapplyAfter").value(initial.plus(Duration.ofDays(7)).toString()));
        mvc.perform(get(adminPath("expediente")).with(auth(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        Map<String,Object> reapply=new HashMap<>();reapply.put("expectedVersion",version);reapply.put("operationKey",UUID.randomUUID().toString());reapply.put("invitationCode","654321");
        mvc.perform(post(applicantPath("reapply")).header("X-Onboarding-Token",token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(reapply))).andExpect(status().isConflict());
        clock.set(initial.plus(Duration.ofDays(7)));token=login();invitation("654321",false);
        JsonNode next=node(mvc.perform(post(applicantPath("reapply")).header("X-Onboarding-Token",token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reapply)))
                .andExpect(status().isOk()).andReturn());
        assertThat(next.path("requestId").asText()).isNotEqualTo(requestId.toString());
        assertThat(count("select count(*) from conductor_onboarding_identity where id=?",identityId)).isEqualTo(1);
        assertThat(count("select count(*) from conductor_onboarding_request where identity_id=?",identityId)).isEqualTo(2);
        assertThat(count("select count(*) from conductor_onboarding_request where identity_id=? and status='PENDING_REVIEW'",identityId)).isEqualTo(1);
        mvc.perform(post(applicantPath("reapply")).header("X-Onboarding-Token",token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(reapply))).andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(next.path("requestId").asText()));
    }

    @Test void motivatedExtensionDoesNotAddRoundAndStaleOrShorterExtensionIsRejected()throws Exception {
        adminAction("return",decision(0,"Completar requisitos pendientes")).andExpect(status().isOk());
        Map<String,Object> extended=decision(1,"Ampliacion motivada por comprobacion pendiente");
        extended.put("deadline",initial.plus(Duration.ofHours(72)).toString());
        adminAction("extend",extended).andExpect(status().isOk()).andExpect(jsonPath("$.reviewRounds").value(1));
        adminAction("extend",extended).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        Map<String,Object> shorter=decision(2,"Intento reducir plazo");shorter.put("deadline",initial.plus(Duration.ofHours(60)).toString());
        adminAction("extend",shorter).andExpect(status().isConflict());
        Map<String,Object> stale=decision(1,"Version antigua");stale.put("deadline",initial.plus(Duration.ofHours(96)).toString());
        adminAction("extend",stale).andExpect(status().isConflict());
        assertThat(count("select count(*) from conductor_onboarding_review_event where action='DEADLINE_EXTENDED'")).isEqualTo(1);
    }

    @Test void sameDocumentAcrossAnotherEmailAndOrganizationCannotCreateSecondPersonOrConsumeQuota()throws Exception {
        saveAdmin(0,reviewedDocuments()).andExpect(status().isOk());
        adminAction("approve",decision(1,"Revision sintetica conforme")).andExpect(status().isOk());
        TransportGroup first=groups.save(TransportGroup.active(organization,"Synthetic first group",null));
        Map<String,Object> provision=decision(2,"Provision inicial");provision.put("groupId",first.getId().toString());
        adminAction("provision",provision).andExpect(status().isOk());
        organization=otherOrganization;admin=otherAdmin;identityId=UUID.randomUUID();requestId=UUID.randomUUID();
        applicantEmail="another.synthetic."+UUID.randomUUID()+"@rutafija.test";
        jdbc.update("""
                insert into conductor_onboarding_identity(id,email,password_hash,full_name,email_verified_at,created_at,updated_at)
                values(?,?,?,?,?,?,?)
                """,identityId,applicantEmail,passwords.encode(PASSWORD),"Synthetic Documentary Applicant",ts(initial),ts(initial),ts(initial));
        UUID invitation=invitation("321654",true);
        jdbc.update("""
                insert into conductor_onboarding_request(id,identity_id,organization_id,invitation_id,idempotency_key,submitted_at)
                values(?,?,?,?,?,?)
                """,requestId,identityId,organization.getId(),invitation,"fixture-"+UUID.randomUUID(),ts(initial));
        saveAdmin(0,reviewedDocuments()).andExpect(status().isOk());
        adminAction("approve",decision(1,"Revision personal separada")).andExpect(status().isOk());
        TransportGroup second=groups.save(TransportGroup.active(organization,"Synthetic second group",null));
        Map<String,Object> duplicate=decision(2,"Intento persona duplicada");duplicate.put("groupId",second.getId().toString());
        adminAction("provision",duplicate).andExpect(status().isConflict());
        assertThat(count("select count(*) from app_user where role='CONDUCTOR'")).isEqualTo(1);
        assertThat(count("select count(*) from pilot_account_creation where role='CONDUCTOR'")).isEqualTo(1);
        assertThat(count("select count(*) from conductor_person_binding")).isEqualTo(1);
    }

    @Test void anotherTenantApplicantOrRoleCannotReadOrMutateTheRestrictedExpediente()throws Exception {
        mvc.perform(get(adminPath("documentary-expediente")).with(auth(otherAdmin))).andExpect(status().isNotFound());
        mvc.perform(post(adminPath("approve")).with(auth(otherAdmin)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(decision(0,"Ajena")))).andExpect(status().isNotFound());
        mvc.perform(get(adminPath("documentary-expediente")).header("X-Onboarding-Token",token)).andExpect(status().isUnauthorized());
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",requestId.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/onboarding/applicant/requests/{id}/documentary-status",UUID.randomUUID()).header("X-Onboarding-Token",token)).andExpect(status().isNotFound());
        mvc.perform(put(applicantPath("documents")).header("X-Onboarding-Token",token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(saveCommand(0,reviewedDocuments())))).andExpect(status().isForbidden());
        mvc.perform(get(adminPath("documentary-expediente")).with(auth(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(5));
        assertThat(count("select count(*) from conductor_document_revision")).isZero();
    }

    @Test void concurrentProvisioningCreatesOnePersonAccountAndDriverAndStaleVersionIsRejected()throws Exception {
        saveAdmin(0,reviewedDocuments()).andExpect(status().isOk());
        adminAction("approve",decision(1,"Revision sintetica terminada")).andExpect(status().isOk());
        TransportGroup group=groups.save(TransportGroup.active(organization,"Synthetic concurrent group",null));
        CountDownLatch ready=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=()->{ready.await();Map<String,Object> command=decision(2,"Provision concurrente");command.put("groupId",group.getId().toString());return mvc.perform(post(adminPath("provision")).with(auth(admin))
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))).andReturn().getResponse().getStatus();};
            var first=executor.submit(attempt);var second=executor.submit(attempt);ready.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(count("select count(*) from app_user where email=?",applicantEmail)).isEqualTo(1);
        assertThat(count("select count(*) from driver")).isEqualTo(1);
        assertThat(count("select count(*) from conductor_person_binding")).isEqualTo(1);
    }

    @Test void reapplicationKeepsFiveFailureEmailLimitIndependentFromOtp()throws Exception {
        adminAction("reject",decision(0,"Solicitud rechazada con motivo")).andExpect(status().isOk());
        for(int attempt=1;attempt<=5;attempt++) {
            var result=mvc.perform(post(applicantPath("reapply")).header("X-Onboarding-Token",token)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("expectedVersion",1,
                            "operationKey",UUID.randomUUID().toString(),"invitationCode","999999"))));
            result.andExpect(attempt==5?status().isTooManyRequests():status().isBadRequest());
        }
        assertThat(count("select count(*) from conductor_onboarding_invitation_failure")).isEqualTo(5);
        assertThat(count("select count(*) from conductor_onboarding_request")).isEqualTo(1);
        assertThat(count("select count(*) from conductor_onboarding_email_challenge")).isZero();
        verifyNoInteractions(email);
    }

    @Test void fiftiethFailurePausesOnlyTheInvitationOriginForExactlyFifteenMinutes()throws Exception {
        adminAction("reject",decision(0,"Rechazo sintetico motivado")).andExpect(status().isOk());
        String origin=hasher.hashOrigin("127.0.0.1");
        for(int attempt=0;attempt<49;attempt++)jdbc.update("""
                insert into conductor_onboarding_invitation_failure(id,identity_email_hash,origin_hash,failed_at)
                values(?,?,?,?)
                """,UUID.randomUUID(),hasher.hashOrigin("synthetic-other-identity-"+attempt),origin,ts(initial));
        mvc.perform(post(applicantPath("reapply")).header("X-Onboarding-Token",token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("expectedVersion",1,"operationKey",UUID.randomUUID().toString(),"invitationCode","999999"))))
                .andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("select blocked_until from conductor_onboarding_invitation_guard where guard_type='ORIGIN' and guard_hash=?",
                Timestamp.class,origin).toInstant()).isEqualTo(initial.plus(Duration.ofMinutes(15)));
        invitation("654321",false);
        Map<String,Object> command=Map.of("expectedVersion",1,"operationKey",UUID.randomUUID().toString(),"invitationCode","654321");
        clock.set(initial.plusSeconds(15*60-1));
        mvc.perform(post(applicantPath("reapply")).header("X-Onboarding-Token",token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command))).andExpect(status().isTooManyRequests());
        mvc.perform(get(applicantPath("documentary-status")).header("X-Onboarding-Token",token)).andExpect(status().isOk());
        clock.set(initial.plusSeconds(15*60));
        mvc.perform(post(applicantPath("reapply")).header("X-Onboarding-Token",token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command))).andExpect(status().isOk());
        assertThat(count("select count(*) from conductor_onboarding_request where identity_id=?",identityId)).isEqualTo(2);
        verifyNoInteractions(email);
    }

    private List<Map<String,Object>> reviewedDocuments() {
        List<Map<String,Object>> documents=new ArrayList<>();
        documents.add(document("CON-01",Map.of("documentType","DNI","documentNumber","12345678","fullName","Synthetic Documentary Applicant","issuer","SYNTHETIC"),"CONFORME"));
        Map<String,Object> license=document("CON-02",Map.of("licenseNumber","SYN-LICENSE","licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC"),"CONFORME");
        license.put("expiryMode","DATED");license.put("validUntil",initial.plus(Duration.ofDays(60)).toString());documents.add(license);
        for(String id:List.of("CON-03","CON-04","CON-05"))documents.add(document(id,Map.of(),"NO_APLICA_JUSTIFICADO"));
        return documents;
    }
    private Map<String,Object> document(String id,Map<String,String> fields,String result) {
        Map<String,Object> value=new HashMap<>();value.put("requirementId",id);value.put("fields",fields);value.put("expiryMode","NO_EXPIRY");
        value.put("validUntil",null);value.put("applicability","CONFIRMED");value.put("condition","SYNTHETIC test-only applicability decision");
        value.put("sourceReference","SYNTHETIC title reference; no external habilitation claim");value.put("result",result);
        value.put("reviewReference","SYNTHETIC controlled visual inspection");value.put("observation","Regla sintetica de prueba explicitamente no aplicable");return value;
    }
    private Map<String,Object> saveCommand(long version,List<Map<String,Object>> documents){Map<String,Object> value=new HashMap<>();value.put("expectedVersion",version);value.put("operationKey",UUID.randomUUID().toString());value.put("modality","TAXI");value.put("documents",documents);return value;}
    private Map<String,Object> decision(long version,String reason){Map<String,Object> value=new HashMap<>();value.put("expectedVersion",version);value.put("operationKey",UUID.randomUUID().toString());value.put("reason",reason);value.put("reiteration",false);return value;}
    private Result saveAdmin(long version,List<Map<String,Object>> documents)throws Exception{return new Result(mvc.perform(put(adminPath("documentary-expediente")).with(auth(admin)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(saveCommand(version,documents)))));}
    private Result adminAction(String action,Map<String,Object> command)throws Exception{return new Result(mvc.perform(post(adminPath(action)).with(auth(admin)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))));}
    private String adminPath(String tail){return "/api/v1/onboarding/requests/"+requestId+"/"+tail;}
    private String applicantPath(String tail){return "/api/v1/onboarding/applicant/requests/"+requestId+"/"+tail;}
    private JsonNode node(MvcResult result)throws Exception{return mapper.readTree(result.getResponse().getContentAsByteArray());}
    private String login()throws Exception{return node(mvc.perform(post("/api/v1/onboarding/access/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("email",applicantEmail,"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn()).path("accessToken").asText();}
    private UUID invitation(String code,boolean used){UUID id=UUID.randomUUID();Instant now=clock.instant();jdbc.update("""
            insert into organization_invitation(id,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at,used_at)
            values(?,?,?,?,?,?,?,?)
            """,id,organization.getId(),admin.getId(),applicantEmail,hasher.hashCode(code,applicantEmail),ts(now),ts(now.plus(Duration.ofHours(120))),used?ts(now):null);return id;}
    private AppUser admin(Organization org,String email){AppUser user=AppUser.organizationUser(org,email,passwords.encode(PASSWORD),"Synthetic ADMIN",UserRole.ADMIN);user.verifyEmail(initial);return users.save(user);}
    private RequestPostProcessor auth(AppUser user){return jwt().authorities(new SimpleGrantedAuthority("ROLE_"+user.getRole().name())).jwt(value->{
        value.subject(user.getId().toString()).claim("email",user.getEmail()).claim("role",user.getRole().name()).claim("sessionVersion",user.getSessionVersion());
        if(user.getOrganizationId()!=null)value.claim("organizationId",user.getOrganizationId().toString());});}
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private static Timestamp ts(Instant value){return Timestamp.from(value);}
    private final class Result {
        private final org.springframework.test.web.servlet.ResultActions actions;
        Result(org.springframework.test.web.servlet.ResultActions actions){this.actions=actions;}
        Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher)throws Exception{actions.andExpect(matcher);return this;}
        JsonNode andReturnNode()throws Exception{return node(actions.andReturn());}
    }
    @TestConfiguration static class ClockConfiguration {@Bean @Primary DocumentaryClock documentaryClock(){return new DocumentaryClock();}}
    static class DocumentaryClock extends Clock {
        private final AtomicReference<Instant> value=new AtomicReference<>(Instant.now());
        void set(Instant instant){value.set(instant);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return value.get();}
    }
}
