package pe.rutafija.invitation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.EmailOtpChallenge;
import pe.rutafija.identity.domain.EmailOtpPurpose;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.EmailOtpChallengeRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.invitation.infrastructure.InvitationJdbcRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO",
        "logging.level.org.hibernate.SQL=INFO"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvitationFlowIT extends NativePostgresIntegrationTest {

    private static final String TEST_PASSWORD = "Invitation-Test-Only-Password-2026";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    EmailOtpChallengeRepository otpChallengeRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    InvitationCodeHasher codeHasher;

    @Autowired
    InvitationJdbcRepository invitationRepository;

    private Organization organization;
    private AppUser admin;
    private AppUser conductor;
    private AppUser secondConductor;
    private AppUser otherTenantAdmin;

    @BeforeEach
    void createVerifiedUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Invitation Test Organization " + suffix,
                "Invitation Test " + suffix,
                "America/Lima"
        ));
        admin = saveVerifiedUser(organization, "invite.admin." + suffix + "@rutafija.test", UserRole.ADMIN);
        conductor = saveVerifiedUser(organization, "invite.driver." + suffix + "@rutafija.test", UserRole.CONDUCTOR);
        secondConductor = saveVerifiedUser(
                organization,
                "invite.second-driver." + suffix + "@rutafija.test",
                UserRole.CONDUCTOR
        );

        Organization otherOrganization = organizationRepository.save(Organization.active(
                "Other Invitation Test Organization " + suffix,
                "Other Invitation Test " + suffix,
                "America/Lima"
        ));
        otherTenantAdmin = saveVerifiedUser(
                otherOrganization,
                "invite.other-admin." + suffix + "@rutafija.test",
                UserRole.ADMIN
        );
    }

    @Test
    void adminCanIssueListAndRevokeWithinOnlyTheAuthenticatedTenant() throws Exception {
        JsonNode first = json(mockMvc.perform(post("/api/v1/invitations")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(conductor.getEmail())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsByteArray());
        String firstCode = first.path("code").asText();
        UUID firstId = UUID.fromString(first.path("id").asText());

        assertThat(firstCode).matches("[0-9]{6}");
        assertThat(first.path("expiresAt").asText()).isEqualTo(
                Instant.parse(first.path("issuedAt").asText()).plus(Duration.ofHours(120)).toString()
        );
        assertThat(jdbcTemplate.queryForObject(
                "select code_hash from organization_invitation where id = ?",
                String.class,
                firstId
        ).trim()).isEqualTo(codeHasher.hashCode(firstCode, conductor.getEmail()));
        assertThat(jdbcTemplate.queryForObject(
                "select code_hash from organization_invitation where id = ?",
                String.class,
                firstId
        ).trim()).isNotEqualTo(firstCode);

        JsonNode second = json(mockMvc.perform(post("/api/v1/invitations")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(conductor.getEmail())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
        UUID secondId = UUID.fromString(second.path("id").asText());
        String secondCode = second.path("code").asText();

        assertThat(jdbcTemplate.queryForObject(
                "select revoked_at from organization_invitation where id = ?",
                java.sql.Timestamp.class,
                firstId
        )).isNotNull();

        mockMvc.perform(post("/api/v1/invitations/validate")
                        .with(authenticatedAs(secondConductor))
                        .with(remoteAddress("198.51.100.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validateBody(secondCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        mockMvc.perform(post("/api/v1/invitations/validate")
                        .with(authenticatedAs(conductor))
                        .with(remoteAddress("198.51.100.10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validateBody(firstCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.organizationName").doesNotExist());

        mockMvc.perform(post("/api/v1/invitations/validate")
                        .with(authenticatedAs(conductor))
                        .with(remoteAddress("198.51.100.10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validateBody(secondCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.invitationId").value(secondId.toString()))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.organizationName").doesNotExist());

        mockMvc.perform(get("/api/v1/invitations")
                        .with(authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].code").doesNotExist())
                .andExpect(jsonPath("$.items[1].codeHash").doesNotExist());

        mockMvc.perform(post("/api/v1/invitations/{id}/revoke", secondId)
                        .with(authenticatedAs(otherTenantAdmin)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/invitations/{id}/revoke", secondId)
                        .with(authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());
    }

    @Test
    void invitationAndFailureWindowUseStrictExactTimeBoundaries() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v1/invitations")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(conductor.getEmail())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
        UUID invitationId = UUID.fromString(created.path("id").asText());
        String code = created.path("code").asText();
        String codeHash = codeHasher.hashCode(code, conductor.getEmail());
        Instant expiryBoundary = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update(
                "update organization_invitation set expires_at = ? where id = ?",
                java.sql.Timestamp.from(expiryBoundary),
                invitationId
        );

        assertThat(invitationRepository.findActiveInvitation(
                conductor.getEmail(), codeHash, expiryBoundary.minusMillis(1)
        )).isPresent();
        assertThat(invitationRepository.findActiveInvitation(
                conductor.getEmail(), codeHash, expiryBoundary
        )).isEmpty();

        Instant failureCutoff = Instant.parse("2026-10-14T10:00:00Z");
        String originHash = codeHasher.hashOrigin("198.51.100.41");
        invitationRepository.recordFailure(conductor.getId(), originHash, failureCutoff);
        invitationRepository.recordFailure(conductor.getId(), originHash, failureCutoff.plusMillis(1));

        assertThat(invitationRepository.countFailuresForAccountSince(conductor.getId(), failureCutoff))
                .isEqualTo(1);
        assertThat(invitationRepository.countFailuresForOriginSince(originHash, failureCutoff))
                .isEqualTo(1);
    }

    @Test
    void fiveAccountFailuresPauseOnlyInvitationValidationAndDoNotTouchOtpState() throws Exception {
        Instant now = Instant.now();
        EmailOtpChallenge otpChallenge = EmailOtpChallenge.create(conductor, now);
        otpChallenge.issueCode(
                "a".repeat(64),
                "b".repeat(64),
                now,
                now.plus(Duration.ofMinutes(5)),
                now.plus(Duration.ofMinutes(15)),
                Duration.ofHours(1),
                EmailOtpPurpose.LOGIN,
                conductor.getEmail(),
                null
        );
        otpChallengeRepository.saveAndFlush(otpChallenge);
        int otpFailuresBefore = jdbcTemplate.queryForObject(
                "select failed_attempts from email_otp_challenge where user_id = ?",
                Integer.class,
                conductor.getId()
        );

        Instant accountLimitStarted = Instant.now();
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(validate(conductor, "000000", "198.51.100.20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }
        mockMvc.perform(validate(conductor, "000000", "198.51.100.20"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("INVITATION_VALIDATION_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        Instant accountLimitFinished = Instant.now();

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from invitation_validation_failure where user_id = ?",
                Integer.class,
                conductor.getId()
        )).isEqualTo(5);
        java.sql.Timestamp accountBlockedUntil = jdbcTemplate.queryForObject(
                "select blocked_until from invitation_validation_account_guard where user_id = ?",
                java.sql.Timestamp.class,
                conductor.getId()
        );
        assertThat(accountBlockedUntil).isNotNull();
        assertThat(accountBlockedUntil.toInstant()).isBetween(
                accountLimitStarted.plus(Duration.ofMinutes(30)),
                accountLimitFinished.plus(Duration.ofMinutes(30))
        );
        assertThat(jdbcTemplate.queryForObject(
                "select failed_attempts from email_otp_challenge where user_id = ?",
                Integer.class,
                conductor.getId()
        )).isEqualTo(otpFailuresBefore);

        mockMvc.perform(validate(secondConductor, "000000", "198.51.100.20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from invitation_validation_failure where user_id = ?",
                Integer.class,
                secondConductor.getId()
        )).isEqualTo(1);
    }

    @Test
    void fiftiethOriginFailurePausesOnlyThisEndpointForThatOrigin() throws Exception {
        List<AppUser> conductors = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            conductors.add(saveVerifiedUser(
                    organization,
                    "invite.limit." + index + "." + UUID.randomUUID().toString().substring(0, 8)
                            + "@rutafija.test",
                    UserRole.CONDUCTOR
            ));
        }

        Instant originLimitStarted = Instant.now();
        for (AppUser account : conductors) {
            for (int attempt = 1; attempt <= 5; attempt++) {
                var result = mockMvc.perform(validate(account, "000000", "198.51.100.30"));
                if (attempt == 5) {
                    result.andExpect(status().isTooManyRequests());
                } else {
                    result.andExpect(status().isOk());
                }
            }
        }
        Instant originLimitFinished = Instant.now();

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from invitation_validation_failure where origin_hash = ?",
                Integer.class,
                codeHasher.hashOrigin("198.51.100.30")
        )).isEqualTo(50);
        java.sql.Timestamp originPausedUntil = jdbcTemplate.queryForObject(
                "select paused_until from invitation_validation_origin_guard where origin_hash = ?",
                java.sql.Timestamp.class,
                codeHasher.hashOrigin("198.51.100.30")
        );
        assertThat(originPausedUntil).isNotNull();
        assertThat(originPausedUntil.toInstant()).isBetween(
                originLimitStarted.plus(Duration.ofMinutes(15)),
                originLimitFinished.plus(Duration.ofMinutes(15))
        );

        mockMvc.perform(validate(secondConductor, "000000", "198.51.100.30"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(validate(secondConductor, "000000", "198.51.100.31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
        mockMvc.perform(get("/api/v1/invitations")
                        .with(authenticatedAs(admin))
                        .with(remoteAddress("198.51.100.30")))
                .andExpect(status().isOk());
    }

    @Test
    void validationRequiresAnAuthenticatedVerifiedConductor() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validateBody("000000")))
                .andExpect(status().isUnauthorized());

        AppUser unverified = userRepository.save(AppUser.organizationUser(
                organization,
                "unverified." + UUID.randomUUID().toString().substring(0, 8) + "@rutafija.test",
                passwordEncoder.encode(TEST_PASSWORD),
                "Unverified test account",
                UserRole.CONDUCTOR
        ));
        mockMvc.perform(validate(unverified, "000000", "198.51.100.40"))
                .andExpect(status().isUnauthorized());
    }

    private AppUser saveVerifiedUser(Organization tenant, String email, UserRole role) {
        AppUser user = AppUser.organizationUser(
                tenant,
                email,
                passwordEncoder.encode(TEST_PASSWORD),
                "Invitation test " + role,
                role
        );
        user.verifyEmail(Instant.now());
        return userRepository.save(user);
    }

    private RequestPostProcessor authenticatedAs(AppUser user) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .jwt(token -> token
                        .subject(user.getId().toString())
                        .claim("organizationId", user.getOrganizationId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name())
                        .claim("sessionVersion", user.getSessionVersion()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validate(
            AppUser user,
            String code,
            String remoteAddress
    ) {
        return post("/api/v1/invitations/validate")
                .with(authenticatedAs(user))
                .with(remoteAddress(remoteAddress))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validateBody(code));
    }

    private RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String createBody(String recipientEmail) {
        return "{\"recipientEmail\":\"" + recipientEmail + "\"}";
    }

    private String validateBody(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    private JsonNode json(byte[] body) throws Exception {
        return objectMapper.readTree(body);
    }
}
