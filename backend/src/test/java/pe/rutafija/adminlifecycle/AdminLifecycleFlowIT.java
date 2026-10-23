package pe.rutafija.adminlifecycle;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.*;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;
import pe.rutafija.terms.application.AdminTermsService;
import pe.rutafija.terms.domain.AdminTermsContentHash;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic accounts, credentials and mail doubles only; no owner's key or actual terms are used. */
@SpringBootTest(properties={"debug=false","logging.level.root=INFO","logging.level.org.hibernate.SQL=INFO"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
@Import(AdminLifecycleFlowIT.ClockConfiguration.class)
class AdminLifecycleFlowIT extends NativePostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder passwords;
    @Autowired OpaqueTokenService tokens;
    @Autowired JwtService jwtService;
    @Autowired ActivationClock clock;
    @MockitoBean OtpEmailSender mail;
    private final Map<String,String> codes=new ConcurrentHashMap<>();
    private AppUser owner;
    private Organization organization;
    private Instant initial;
    private static final String PASSWORD="Synthetic-Activation-Password-2026";
    private static final String HEADER="X-Admin-Activation-Token";
    private final AtomicInteger syntheticDni=new AtomicInteger();

    @BeforeEach void prepare() throws Exception {
        initial=Instant.now().truncatedTo(ChronoUnit.MICROS);clock.set(initial);
        owner=AppUser.superAdmin("synthetic.owner."+UUID.randomUUID()+"@rutafija.test",passwords.encode(PASSWORD),"Synthetic SUPER_ADMIN");
        owner.verifyEmail(initial);owner=users.save(owner);
        syntheticDni.set(40000000);
        mvc.perform(post("/api/v1/platform/identity/self-enrollment").with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of(
                        "currentPassword",PASSWORD,"ownerDeclaration",true,"identityReview",identityReview("90000001")))))
                .andExpect(status().isOk());
        organization=organizations.save(Organization.active("Synthetic ADMIN Organization "+UUID.randomUUID(),"Synthetic ADMIN","America/Lima"));
        codes.clear();doAnswer(invocation->{codes.put(invocation.getArgument(0),invocation.getArgument(1));return null;})
                .when(mail).sendVerificationCode(any(String.class),any(String.class),any(Instant.class));
    }

    @Test void creationConsumesHistoricalSlotInactiveAndCredentialIsSingleUseWithPrivateThirtyMinuteScope()throws Exception {
        Candidate candidate=create();
        assertThat(candidate.created.path("credential").asText()).matches("ac1\\.[A-Za-z0-9_-]{43}");
        assertThat(Instant.parse(candidate.created.path("credentialExpiresAt").asText())).isEqualTo(initial.plus(Duration.ofHours(24)));
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,candidate.userId())).isFalse();
        assertThat(jdbc.queryForObject("select credential_hash from admin_activation where id=?",String.class,candidate.activationId()).trim())
                .isEqualTo(tokens.hash(candidate.created.path("credential").asText()));
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(1);
        mvc.perform(get("/api/v1/platform/admins").with(auth(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").doesNotExist()).andExpect(jsonPath("$[0].fullName").doesNotExist())
                .andExpect(jsonPath("$[0].credential").doesNotExist()).andExpect(jsonPath("$[0].active").value(false));
        String token=claim(candidate);
        mvc.perform(get("/api/v1/admin-activation/status").header(HEADER,token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS")).andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,candidate.userId()))
                .matches(hash->passwords.matches(PASSWORD,hash));
        mvc.perform(post("/api/v1/admin-activation/claim").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("credential",candidate.created.path("credential").asText(),"newPassword",PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header(HEADER,token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION,"Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin-activation/terms").header(HEADER,token)).andExpect(status().isConflict());
        assertLogin(candidate.email,401);
        verifyNoInteractions(mail);
    }

    @Test void explicitSyntheticTermsAndVerifiedOtpActivateWithoutIssuingOperationalSession()throws Exception {
        Candidate candidate=create();String token=claim(candidate);verifyEmail(candidate,token);publish("SYNTHETIC-ACTIVATION-1");
        JsonNode terms=node(mvc.perform(get("/api/v1/admin-activation/terms").header(HEADER,token)).andExpect(status().isOk()).andReturn());
        assertThat(terms.path("synthetic").asBoolean()).isTrue();
        Map<String,Object> acceptance=acceptance(terms);acceptance.put("accepted",false);
        mvc.perform(post("/api/v1/admin-activation/terms").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(acceptance))).andExpect(status().isBadRequest());
        acceptance.put("accepted",true);acceptance.put("contentSha256","0".repeat(64));
        mvc.perform(post("/api/v1/admin-activation/terms").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(acceptance))).andExpect(status().isConflict());
        accept(token,terms);
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.refreshToken").doesNotExist());
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,candidate.userId())).isTrue();
        assertThat(count("select count(*) from admin_terms_acceptance where admin_user_id=? and organization_id=?",candidate.userId(),organization.getId())).isEqualTo(1);
        mvc.perform(get("/api/v1/admin-activation/status").header(HEADER,token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isOk());
        assertThat(count("select count(*) from admin_activation_event where action='ACTIVATION_COMPLETED'")).isEqualTo(1);
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(1);
        assertLogin(candidate.email,200);
    }

    @Test void missingOrChangedCurrentTermsFailClosedWhileCandidateRemainsInactive()throws Exception {
        Candidate candidate=create();String token=claim(candidate);verifyEmail(candidate,token);
        mvc.perform(get("/api/v1/admin-activation/terms").header(HEADER,token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isConflict());
        publish("SYNTHETIC-ACTIVATION-1");JsonNode first=currentTerms(token);accept(token,first);
        clock.set(initial.plusSeconds(1));publish("SYNTHETIC-ACTIVATION-2");
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,candidate.userId())).isFalse();
        accept(token,currentTerms(token));
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isOk());
        assertThat(count("select count(*) from admin_terms_acceptance where admin_user_id=?",candidate.userId())).isEqualTo(2);
    }

    @Test void authorizedReplacementKeepsOutgoingActiveUntilAtomicCompletionAndRevokesItsSessions()throws Exception {
        AppUser outgoing=activeAdmin("outgoing");
        String oldJwt=jwtService.issueAccessToken(outgoing);
        var refresh=refreshTokens.save(RefreshToken.firstInFamily(outgoing,tokens.hash(tokens.generate()),initial.plus(Duration.ofDays(1))));
        String email="synthetic.replacement."+UUID.randomUUID()+"@rutafija.test";
        Map<String,Object> command=replacement(email);command.put("identityChecked",false);
        mvc.perform(post("/api/v1/platform/admins/{id}/replacement",outgoing.getId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command))).andExpect(status().isBadRequest());
        command.put("identityChecked",true);
        JsonNode created=node(mvc.perform(post("/api/v1/platform/admins/{id}/replacement",outgoing.getId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command))).andExpect(status().isOk()).andReturn());
        Candidate replacement=new Candidate(created,email);
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,outgoing.getId())).isTrue();
        String token=claim(replacement);verifyEmail(replacement,token);publish("SYNTHETIC-REPLACEMENT-1");accept(token,currentTerms(token));
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,outgoing.getId())).isFalse();
        assertThat(jdbc.queryForObject("select session_version from app_user where id=?",Long.class,outgoing.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select revoked_at from refresh_token where id=?",Timestamp.class,refresh.getId())).isNotNull();
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION,"Bearer "+oldJwt)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,replacement.userId())).isTrue();
        assertThat(count("select count(*) from app_user where id=?",outgoing.getId())).isEqualTo(1);
    }

    @Test void fourthHistoricalCreationBlocksFifthReplacementWithoutDisablingOutgoingOrDeletingHistory()throws Exception {
        AppUser outgoing=activeAdmin("quota-outgoing");create();create();create();
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(4);
        mvc.perform(post("/api/v1/platform/admins/{id}/replacement",outgoing.getId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(replacement("synthetic.fifth."+UUID.randomUUID()+"@rutafija.test"))))
                .andExpect(status().isConflict());
        assertThat(count("select count(*) from admin_activation")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,outgoing.getId())).isTrue();
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(4);
        verifyNoInteractions(mail);
    }

    @Test void securityRevocationAfterPreparingReplacementDoesNotActivateCandidateAndAllowsVerifiedCompletion()throws Exception {
        AppUser outgoing=activeAdmin("security-after");Candidate candidate=prepareReplacement(outgoing);
        revoke(outgoing);
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,candidate.userId())).isFalse();
        String token=claim(candidate);verifyEmail(candidate,token);publish("SYNTHETIC-SECURITY-AFTER");accept(token,currentTerms(token));
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,outgoing.getId())).isFalse();
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,candidate.userId())).isTrue();
    }

    @Test void securityRevocationBeforePreparationKeepsHistoryAndAllowsSameOrganizationReplacement()throws Exception {
        AppUser outgoing=activeAdmin("security-before");revoke(outgoing);Candidate candidate=prepareReplacement(outgoing);
        assertThat(count("select count(*) from admin_security_revocation where admin_user_id=?",outgoing.getId())).isEqualTo(1);
        String token=claim(candidate);verifyEmail(candidate,token);publish("SYNTHETIC-SECURITY-BEFORE");accept(token,currentTerms(token));
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isOk());
        assertThat(count("select count(*) from app_user where id=?",outgoing.getId())).isEqualTo(1);
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(2);
    }

    @Test void arbitrarilyInactiveOutgoingWithoutRecordedSuperAdminRevocationCannotAuthorizeReplacement()throws Exception {
        AppUser outgoing=activeAdmin("unrecorded-inactive");outgoing.deactivate();users.saveAndFlush(outgoing);
        mvc.perform(post("/api/v1/platform/admins/{id}/replacement",outgoing.getId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(replacement("synthetic.unrecorded."+UUID.randomUUID()+"@rutafija.test"))))
                .andExpect(status().isConflict());
        assertThat(count("select count(*) from admin_activation")).isZero();
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(1);
    }

    @Test void securityRevocationOfPendingCandidateInvalidatesItsActivationScopeUntilExplicitReissue()throws Exception {
        Candidate candidate=create();String token=claim(candidate);verifyEmail(candidate,token);publish("SYNTHETIC-PENDING-REVOKE");accept(token,currentTerms(token));
        mvc.perform(post("/api/v1/platform/admins/{id}/revoke",candidate.userId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("reason","Bloqueo preventivo de credenciales pendientes"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin-activation/complete").header(HEADER,token)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,candidate.userId())).isFalse();
        claim(rotate(candidate));
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(1);
    }

    @Test void simultaneousClaimConsumesCredentialOnlyOnce()throws Exception {
        Candidate candidate=create();CountDownLatch ready=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=()->{ready.await();return mvc.perform(post("/api/v1/admin-activation/claim").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(Map.of("credential",candidate.created.path("credential").asText(),"newPassword",PASSWORD))))
                    .andReturn().getResponse().getStatus();};
            var first=executor.submit(attempt);var second=executor.submit(attempt);ready.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }
        assertThat(count("select count(*) from admin_activation_event where action='ACTIVATION_CREDENTIAL_CLAIMED'")).isEqualTo(1);
    }

    @Test void expiryAndRotationKeepSameInactiveAccountAndRevokePreviousScopedCredentials()throws Exception {
        Candidate candidate=create();clock.set(initial.plus(Duration.ofHours(24)));
        mvc.perform(post("/api/v1/admin-activation/claim").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("credential",candidate.created.path("credential").asText(),"newPassword",PASSWORD))))
                .andExpect(status().isUnauthorized());
        Candidate rotated=rotate(candidate);String oldToken=claim(rotated);Candidate again=rotate(candidate);
        mvc.perform(get("/api/v1/admin-activation/status").header(HEADER,oldToken)).andExpect(status().isUnauthorized());
        String token=claim(again);clock.set(clock.instant().plus(Duration.ofMinutes(30)));
        mvc.perform(get("/api/v1/admin-activation/status").header(HEADER,token)).andExpect(status().isUnauthorized());
        assertThat(count("select count(*) from app_user where id=?",candidate.userId())).isEqualTo(1);
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(1);
        verifyNoInteractions(mail);
    }

    @Test void otpPurposeAndExpectedAccountPreventCrossFlowAndCrossCandidateVerification()throws Exception {
        Candidate first=create(),second=create();String firstToken=claim(first),secondToken=claim(second);
        JsonNode challenge=begin(firstToken);String verification=challenge.path("verificationToken").asText();
        Map<String,Object> proof=Map.of("verificationToken",verification,"code",codes.get(first.email));
        mvc.perform(post("/api/v1/admin-activation/otp/verify").header(HEADER,secondToken).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(proof))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(proof)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin-activation/status").header(HEADER,"oa1."+tokens.generate())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select email_verified_at from admin_activation where id=?",Timestamp.class,second.activationId())).isNull();
        mvc.perform(post("/api/v1/admin-activation/otp/verify").header(HEADER,firstToken).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(proof))).andExpect(status().isOk());
    }

    @Test void otpWrongAttemptSurvivesResendAndCredentialRotationCannotRemoveThirtyMinuteBlock()throws Exception {
        Candidate candidate=create();String token=claim(candidate);JsonNode first=begin(token);
        String wrong="000000".equals(codes.get(candidate.email))?"999999":"000000";
        mvc.perform(post("/api/v1/admin-activation/otp/verify").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("verificationToken",first.path("verificationToken").asText(),"code",wrong))))
                .andExpect(status().isBadRequest());
        JsonNode second=node(mvc.perform(post("/api/v1/admin-activation/otp/resend").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("verificationToken",first.path("verificationToken").asText()))))
                .andExpect(status().isOk()).andReturn());
        wrong="000000".equals(codes.get(candidate.email))?"999999":"000000";
        mvc.perform(post("/api/v1/admin-activation/otp/verify").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("verificationToken",second.path("verificationToken").asText(),"code",wrong))))
                .andExpect(status().isTooManyRequests());
        String rotatedToken=claim(rotate(candidate));
        mvc.perform(post("/api/v1/admin-activation/otp/begin").header(HEADER,rotatedToken)).andExpect(status().isTooManyRequests());
        verify(mail,times(2)).sendVerificationCode(any(String.class),any(String.class),any(Instant.class));
    }

    @Test void otpLimitsIncludeInitialAndThreeSendsAndExpireAtFiveMinuteBoundary()throws Exception {
        Candidate candidate=create();String token=claim(candidate);JsonNode challenge=begin(token);
        for(int attempt=0;attempt<2;attempt++)challenge=node(mvc.perform(post("/api/v1/admin-activation/otp/resend").header(HEADER,token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("verificationToken",challenge.path("verificationToken").asText()))))
                .andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/v1/admin-activation/otp/resend").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("verificationToken",challenge.path("verificationToken").asText()))))
                .andExpect(status().isTooManyRequests());
        verify(mail,times(3)).sendVerificationCode(any(String.class),any(String.class),any(Instant.class));
        clock.set(initial.plus(Duration.ofMinutes(5)));
        mvc.perform(post("/api/v1/admin-activation/otp/verify").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("verificationToken",challenge.path("verificationToken").asText(),"code",codes.get(candidate.email)))))
                .andExpect(status().isGone());
        assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,candidate.userId())).isFalse();
    }

    @Test void nonSuperAdminAndInactiveOrganizationsCannotCreateAndSameOrganizationMayHaveSeveralAdmins()throws Exception {
        AppUser admin=activeAdmin("ordinary");
        Map<String,Object> input=createInput("synthetic.denied."+UUID.randomUUID()+"@rutafija.test");
        mvc.perform(post("/api/v1/platform/admins").with(auth(admin)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(input)))
                .andExpect(status().isForbidden());
        Candidate candidate=create();
        assertThat(count("select count(*) from app_user where organization_id=? and role='ADMIN'",organization.getId())).isEqualTo(2);
        input.put("email",candidate.email);
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(input)))
                .andExpect(status().isConflict());
        jdbc.update("update organization set status='INACTIVE' where id=?",organization.getId());input.put("email","synthetic.inactive."+UUID.randomUUID()+"@rutafija.test");
        mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(input)))
                .andExpect(status().isConflict());
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(2);
        verifyNoInteractions(mail);
    }

    private Candidate create()throws Exception {
        String email="synthetic.admin."+UUID.randomUUID()+"@rutafija.test";
        MvcResult response=mvc.perform(post("/api/v1/platform/admins").with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(createInput(email)))).andExpect(status().isOk()).andReturn();
        return new Candidate(node(response),email);
    }
    private Map<String,Object> createInput(String email){Map<String,Object> value=new HashMap<>();value.put("organizationId",organization.getId().toString());value.put("email",email);value.put("fullName","Synthetic ADMIN Candidate");value.put("reason","Alta academica sintetica autorizada");value.put("identityReview",identityReview(Integer.toString(syntheticDni.incrementAndGet())));return value;}
    private Map<String,Object> replacement(String email){Map<String,Object> value=new HashMap<>();value.put("email",email);value.put("fullName","Synthetic Replacement ADMIN");value.put("verificationMethod","IN_PERSON");value.put("verificationReference","SYNTHETIC-LOCAL-VERIFICATION");value.put("reason","Reemplazo academico verificado");value.put("identityChecked",true);value.put("replacementAuthorized",true);value.put("identityReview",identityReview(Integer.toString(syntheticDni.incrementAndGet())));return value;}
    private Map<String,Object> identityReview(String dni){return Map.of("documentType","DNI","documentNumber",dni,"method","IN_PERSON","reference","SYNTHETIC visual identity review","originalReviewed",true,"identityConfirmed",true);}
    private Candidate prepareReplacement(AppUser outgoing)throws Exception {String email="synthetic.replacement."+UUID.randomUUID()+"@rutafija.test";return new Candidate(node(mvc.perform(post("/api/v1/platform/admins/{id}/replacement",outgoing.getId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(replacement(email)))).andExpect(status().isOk()).andReturn()),email);}
    private void revoke(AppUser outgoing)throws Exception {mvc.perform(post("/api/v1/platform/admins/{id}/revoke",outgoing.getId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("reason","Revocacion preventiva de seguridad por SUPER_ADMIN"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));}
    private String claim(Candidate candidate)throws Exception {JsonNode response=node(mvc.perform(post("/api/v1/admin-activation/claim").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("credential",candidate.created.path("credential").asText(),"newPassword",PASSWORD))))
            .andExpect(status().isOk()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn());assertThat(response.path("activationToken").asText()).matches("aa1\\.[A-Za-z0-9_-]{43}");return response.path("activationToken").asText();}
    private Candidate rotate(Candidate candidate)throws Exception {return new Candidate(node(mvc.perform(post("/api/v1/platform/admins/{id}/activation-credential",candidate.userId()).with(auth(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("reason","Reemision local autorizada sin nueva alta")))).andExpect(status().isOk()).andReturn()),candidate.email);}
    private JsonNode begin(String token)throws Exception{return node(mvc.perform(post("/api/v1/admin-activation/otp/begin").header(HEADER,token)).andExpect(status().isOk()).andReturn());}
    private void verifyEmail(Candidate candidate,String token)throws Exception {JsonNode ticket=begin(token);mvc.perform(post("/api/v1/admin-activation/otp/verify").header(HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("verificationToken",ticket.path("verificationToken").asText(),"code",codes.get(candidate.email)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));}
    private JsonNode currentTerms(String token)throws Exception{return node(mvc.perform(get("/api/v1/admin-activation/terms").header(HEADER,token)).andExpect(status().isOk()).andReturn());}
    private Map<String,Object> acceptance(JsonNode document){return new HashMap<>(Map.of("version",document.path("version").asText(),"contentSha256",document.path("contentSha256").asText(),"accepted",true));}
    private void accept(String token,JsonNode document)throws Exception{mvc.perform(post("/api/v1/admin-activation/terms").header(HEADER,token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(acceptance(document))))
            .andExpect(status().isOk());}
    private void publish(String version){String marker="SYNTHETIC-TEST-ONLY-"+version.replace("SYNTHETIC-","");String content="SYNTHETIC TEST DOCUMENT ONLY. NOT APPROVED LEGAL TERMS. "+marker;jdbc.update("insert into admin_terms_document(id,document_key,version,content,content_sha256,synthetic,created_at) values(?,?,?,?,?,true,?)",UUID.randomUUID(),AdminTermsService.ADMIN_TERMS_DOCUMENT_KEY,marker,content,AdminTermsContentHash.sha256(content),Timestamp.from(clock.instant()));}
    private AppUser activeAdmin(String suffix){AppUser user=AppUser.organizationUser(organization,"synthetic.active."+suffix+"."+UUID.randomUUID()+"@rutafija.test",passwords.encode(PASSWORD),"Synthetic Active ADMIN",UserRole.ADMIN);user.verifyEmail(initial);return users.save(user);}
    private RequestPostProcessor auth(AppUser user){return jwt().authorities(new SimpleGrantedAuthority("ROLE_"+user.getRole().name())).jwt(value->{value.subject(user.getId().toString()).claim("role",user.getRole().name()).claim("sessionVersion",user.getSessionVersion());if(user.getOrganizationId()!=null)value.claim("organizationId",user.getOrganizationId().toString());});}
    private void assertLogin(String email,int expected)throws Exception {assertThat(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("email",email,"password",PASSWORD))))
            .andReturn().getResponse().getStatus()).isEqualTo(expected);}
    private JsonNode node(MvcResult result)throws Exception{return mapper.readTree(result.getResponse().getContentAsByteArray());}
    private int count(String query,Object...args){return jdbc.queryForObject(query,Integer.class,args);}
    private record Candidate(JsonNode created,String email){UUID userId(){return UUID.fromString(created.path("adminUserId").asText());}UUID activationId(){return UUID.fromString(created.path("activationId").asText());}}
    @TestConfiguration static class ClockConfiguration {@Bean @Primary ActivationClock activationClock(){return new ActivationClock();}}
    static class ActivationClock extends Clock {private final AtomicReference<Instant> value=new AtomicReference<>(Instant.now());void set(Instant now){value.set(now);}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}@Override public Instant instant(){return value.get();}}
}
