package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.support.L02PostgresIntegrationTest;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual filters, session services and PostgreSQL with rollback-only invented
 * accounts. Complements historical native suites without disabling V51's
 * append-only protections or invoking their incompatible blanket cleanup. */
@SpringBootTest(properties={"debug=false","logging.level.root=ERROR","logging.level.org.hibernate.SQL=OFF",
        "app.qualification.validity-sweep=PT24H"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(QualificationIntegrationTest.ClockConfiguration.class)
class IsolatedAuthLifecycleIntegrationTest extends L02PostgresIntegrationTest {
    private static final String PASSWORD="Synthetic-L02-Password-2026";
    @Autowired AutowireCapableBeanFactory beanFactory;
    @MockitoBean OtpEmailSender mail;
    private QualificationIntegrationTest fixture;

    @BeforeEach void prepare(TestInfo info) throws Exception {
        fixture=new QualificationIntegrationTest();
        beanFactory.autowireBean(fixture);
        fixture.fixture(info);
    }

    private ResultActions jsonPost(String path,Object body) throws Exception {
        return fixture.mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(fixture.mapper.writeValueAsBytes(body)));
    }
    private MvcResult webLogin() throws Exception {
        return jsonPost("/api/v1/auth/login",Map.of("email",fixture.admin.getEmail(),"password",PASSWORD))
                .andExpect(status().isOk()).andReturn();
    }
    private JsonNode body(MvcResult result) throws Exception {
        return fixture.mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    private MvcResult mobileLogin() throws Exception {
        return jsonPost("/api/v1/mobile/auth/login",Map.of("email",fixture.conductor.getEmail(),"password",PASSWORD))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie")).andReturn();
    }

    @Test void webLoginKeepsRefreshInStrictHttpOnlyCookieAndProjectsTheSameTenant() throws Exception {
        var login=webLogin();
        var cookie=login.getResponse().getCookie("rf_refresh");
        assertThat(cookie!=null&&cookie.isHttpOnly()).isTrue();
        assertThat(login.getResponse().getHeader("Set-Cookie").contains("SameSite=Strict")).isTrue();
        assertThat(body(login).has("refreshToken")).isFalse();
        fixture.mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+body(login).path("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.id").value(fixture.admin.getId().toString()))
                .andExpect(jsonPath("$.organizationId").value(fixture.organization.getId().toString()))
                .andExpect(jsonPath("$.email").value(""));
        verifyNoInteractions(mail);
    }

    @Test void wrongPasswordReturnsNormalizedUnauthorizedWithoutSettingACookie() throws Exception {
        jsonPost("/api/v1/auth/login",Map.of("email",fixture.admin.getEmail(),"password","Synthetic-wrong-password"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test void disabledAccountCannotLogIn() throws Exception {
        fixture.admin.deactivate();fixture.users.flush();
        jsonPost("/api/v1/auth/login",Map.of("email",fixture.admin.getEmail(),"password",PASSWORD))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test void refreshRotationAndReuseInvalidateTheWholeFamily() throws Exception {
        Cookie first=webLogin().getResponse().getCookie("rf_refresh");
        var rotated=fixture.mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isOk()).andReturn();
        Cookie next=rotated.getResponse().getCookie("rf_refresh");
        assertThat(next!=null&&!Objects.equals(first.getValue(),next.getValue())).isTrue();
        fixture.mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSE_DETECTED"));
        nextRequestPersistenceContext();
        fixture.mvc.perform(post("/api/v1/auth/refresh").cookie(next)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test void logoutRevokesRefreshAndClearsCookie() throws Exception {
        Cookie first=webLogin().getResponse().getCookie("rf_refresh");
        fixture.mvc.perform(post("/api/v1/auth/logout").cookie(first)).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("rf_refresh",0));
        nextRequestPersistenceContext();
        fixture.mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
    }

    @Test void anonymousOrMalformedBearerCannotReadCurrentIdentity() throws Exception {
        fixture.mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        fixture.mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer synthetic-invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test void adminCannotUseConductorAuthenticationEndpoint() throws Exception {
        jsonPost("/api/v1/mobile/auth/login",Map.of("email",fixture.admin.getEmail(),"password",PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MOBILE_USER_NOT_DRIVER"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test void existingUnqualifiedConductorReceivesLimitedSessionAndRefreshWithoutBrowserCookie() throws Exception {
        var login=mobileLogin();
        assertThat(body(login).path("accessScope").asText()).isEqualTo("INCORPORATION");
        assertThat(body(login).path("refreshToken").asText().startsWith("m1.")).isTrue();
        jsonPost("/api/v1/mobile/auth/refresh",Map.of("refreshToken",body(login).path("refreshToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessScope").value("INCORPORATION"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test void mobileRefreshTokenCannotBecomeABrowserSession() throws Exception {
        var login=mobileLogin();
        fixture.mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("rf_refresh",body(login).path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test void mobileLogoutRevokesItsFamilyWithoutChangingBrowserCookies() throws Exception {
        var login=mobileLogin();String refresh=body(login).path("refreshToken").asText();
        jsonPost("/api/v1/mobile/auth/logout",Map.of("refreshToken",refresh)).andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"));
        nextRequestPersistenceContext();
        jsonPost("/api/v1/mobile/auth/refresh",Map.of("refreshToken",refresh)).andExpect(status().isUnauthorized());
    }

    private void nextRequestPersistenceContext() {
        // MockMvc calls share the outer rollback transaction. Real independent
        // requests do not retain an entity loaded before a bulk revocation.
        // Flush the fixture, then reload database state for the next request.
        fixture.entities.flush();
        fixture.entities.clear();
    }
}
