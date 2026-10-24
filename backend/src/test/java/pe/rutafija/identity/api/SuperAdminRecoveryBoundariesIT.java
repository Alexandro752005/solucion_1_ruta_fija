package pe.rutafija.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Additional protocol boundaries; only temporary accounts/keys and simulated email. */
@SpringBootTest(properties={"debug=false","logging.level.root=WARN","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
@Import(SuperAdminRecoveryBoundariesIT.ClockConfiguration.class)
class SuperAdminRecoveryBoundariesIT extends NativePostgresIntegrationTest {
    private static final String PASSWORD="Synthetic-Owner-Boundary-2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired BoundaryClock clock;
    @MockitoBean OtpEmailSender mail;
    private AppUser owner;
    private KeyPair pair;
    private String emergency;

    @BeforeEach void enrollTemporaryOwner() throws Exception {
        clock.value=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        owner=AppUser.superAdmin("boundary."+UUID.randomUUID()+"@rutafija.test",passwords.encode(PASSWORD),"SYNTHETIC OWNER");
        owner.verifyEmail(clock.value);owner=users.saveAndFlush(owner);
        pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String access=call("/api/v1/auth/login",Map.of("email",owner.getEmail(),"password",PASSWORD),200,null).path("accessToken").asText();
        emergency=call("/api/v1/auth/super-admin-recovery/enrollment",Map.of("publicKeyX509Base64",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),"currentPassword",PASSWORD),201,access).path("emergencyCodeShownOnce").asText();
    }

    @Test void eachAlteredInstallationAccountEmailNonceOrExpiryRejectsAValidKeySignature() throws Exception {
        for(int variant=0;variant<5;variant++){
            String recipient="altered."+variant+"@rutafija.test";JsonNode challenge=start(recipient);
            String canonical=challenge.path("challengeToSignUtf8").asText();
            String altered=switch(variant){
                case 0 -> canonical.replace(challenge.path("installationId").asText(),UUID.randomUUID().toString());
                case 1 -> canonical.replace(challenge.path("accountReference").asText(),UUID.randomUUID().toString());
                case 2 -> canonical.replace(recipient,"different@rutafija.test");
                case 3 -> canonical.replace(challenge.path("nonce").asText(),"A".repeat(43));
                default -> canonical+"0";
            };
            assertThat(altered.equals(canonical)).isFalse();
            call(path(challenge,"proof"),Map.of("emergencyCode",emergency,"signatureBase64Url",sign(altered)),400,null);
            assertThat(jdbc.queryForObject("select failed_proof_attempts from super_admin_recovery_challenge where id=?",Integer.class,
                    UUID.fromString(challenge.path("challengeId").asText()))).isEqualTo(1);
        }
        verifyNoInteractions(mail);
    }

    @Test void challengeExpiresAtTheExactFiveMinuteBoundaryWithoutEmail() throws Exception {
        JsonNode challenge=start("expiry@rutafija.test");
        Instant expiry=Instant.parse(challenge.path("expiresAt").asText());
        assertThat(expiry).isEqualTo(clock.value.plusSeconds(300));clock.value=expiry;
        call(path(challenge,"proof"),Map.of("emergencyCode",emergency,"signatureBase64Url",sign(challenge.path("challengeToSignUtf8").asText())),410,null);
        verifyNoInteractions(mail);
    }

    @Test void concurrentProofAndCompletionEachCommitOnlyOnceAndSendOnlyOneEmail() throws Exception {
        String recipient="concurrent@rutafija.test";JsonNode challenge=start(recipient);
        byte[] proof=json.writeValueAsBytes(Map.of("emergencyCode",emergency,"signatureBase64Url",sign(challenge.path("challengeToSignUtf8").asText())));
        List<MvcResult> proofs=concurrently(path(challenge,"proof"),proof);
        assertThat(proofs.stream().filter(r->r.getResponse().getStatus()==202).count()).isEqualTo(1);
        assertThat(proofs.stream().filter(r->r.getResponse().getStatus()!=202).map(r->r.getResponse().getStatus()).toList()).containsExactly(400);
        JsonNode accepted=json.readTree(proofs.stream().filter(r->r.getResponse().getStatus()==202).findFirst().orElseThrow().getResponse().getContentAsByteArray());
        var captured=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail,times(1)).sendVerificationCode(eq(recipient),captured.capture(),any(Instant.class));
        byte[] complete=json.writeValueAsBytes(Map.of("verificationToken",accepted.path("verificationToken").asText(),"code",captured.getValue(),"newPassword",PASSWORD+"-Recovered"));
        List<MvcResult> results=concurrently(path(challenge,"complete"),complete);
        assertThat(results.stream().filter(r->r.getResponse().getStatus()==200).count()).isEqualTo(1);
        assertThat(results.stream().filter(r->r.getResponse().getStatus()!=200).map(r->r.getResponse().getStatus()).toList()).containsExactly(400);
        assertThat(users.findById(owner.getId()).orElseThrow().getSessionVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from super_admin_recovery_challenge where consumed_at is not null",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app_user",Integer.class)).isEqualTo(1);
        verify(mail,times(1)).sendVerificationCode(eq(recipient),anyString(),any(Instant.class));
        Arrays.fill(proof,(byte)0);Arrays.fill(complete,(byte)0);
    }

    private JsonNode start(String email) throws Exception {return call("/api/v1/auth/super-admin-recovery/challenges",Map.of("accountEmail",owner.getEmail(),"newEmail",email),202,null);}
    private String path(JsonNode challenge,String suffix){return "/api/v1/auth/super-admin-recovery/challenges/"+challenge.path("challengeId").asText()+"/"+suffix;}
    private String sign(String text) throws Exception {
        Signature signature=Signature.getInstance("Ed25519");signature.initSign(pair.getPrivate());signature.update(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }
    private JsonNode call(String path,Map<String,String> body,int expected,String bearer) throws Exception {
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if(bearer!=null)request.header(HttpHeaders.AUTHORIZATION,"Bearer "+bearer);
        return json.readTree(mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    private List<MvcResult> concurrently(String path,byte[] body) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);CyclicBarrier barrier=new CyclicBarrier(2);
        try {
            Callable<MvcResult> call=()->{barrier.await(10,TimeUnit.SECONDS);return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();};
            var first=executor.submit(call);var second=executor.submit(call);
            return List.of(first.get(25,TimeUnit.SECONDS),second.get(25,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
    }
    @TestConfiguration static class ClockConfiguration {@Bean @Primary BoundaryClock boundaryClock(){return new BoundaryClock();}}
    static final class BoundaryClock extends Clock {
        volatile Instant value=Instant.now();
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return value;}
    }
}
