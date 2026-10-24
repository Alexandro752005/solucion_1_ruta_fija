package pe.rutafija.shared.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.sql.SQLTransientConnectionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Real MVC/advice/filter, explicit failure doubles. No database outage or accounts. */
class AvailabilityErrorHttpTest {
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    private MockMvc mvc;
    private static final String SECRET = "SYNTHETIC_SECRET_MUST_NOT_APPEAR";
    private final java.util.Map<ch.qos.logback.classic.Logger,ch.qos.logback.classic.Level> previousLevels=new java.util.HashMap<>();
    private ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> events;
    private ch.qos.logback.classic.Logger root;

    @RestController class Endpoint {
        @PostMapping("/api/v1/mobile/auth/login") void login() { throw failure.get(); }
    }

    @BeforeEach void setup() {
        for(String category:java.util.List.of("org.springframework.web","org.springframework.security","org.springframework.jdbc.core")){
            var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(category);
            previousLevels.put(logger,logger.getLevel());logger.setLevel(ch.qos.logback.classic.Level.WARN);
        }
        root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        events=new ch.qos.logback.core.read.ListAppender<>();events.start();root.addAppender(events);
        mvc = standaloneSetup(new Endpoint()).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter()).build();
    }

    @AfterEach void restoreLoggers(){root.detachAppender(events);events.stop();previousLevels.forEach(ch.qos.logback.classic.Logger::setLevel);}

    @Test void connectionFailureIs503CanonicalAndDoesNotExposeTheCause() throws Exception {
        failure.set(new DataAccessResourceFailureException(SECRET));
        canonical(503, "SERVICE_UNAVAILABLE");
    }

    @Test void wrappedConnectionFailureIs503RatherThanIncorrectCredentials() throws Exception {
        failure.set(new CannotCreateTransactionException(SECRET, new SQLTransientConnectionException(SECRET)));
        canonical(503, "SERVICE_UNAVAILABLE");
    }

    @Test void internalProgrammingErrorRemains500() throws Exception {
        failure.set(new IllegalStateException(SECRET));
        canonical(500, "INTERNAL_ERROR");
    }

    @Test void transactionProgrammingErrorIsNotCalledAnOutage() throws Exception {
        failure.set(new CannotCreateTransactionException(SECRET, new IllegalStateException(SECRET)));
        canonical(500, "INTERNAL_ERROR");
    }

    @Test void invalidCredentialsRemain401WithTheirOwnCode() throws Exception {
        failure.set(new InvalidCredentialsException());
        canonical(401, "AUTH_INVALID_CREDENTIALS");
    }

    @Test void mailDeliveryOutageKeepsItsSpecificCode() throws Exception {
        failure.set(new ExternalServiceUnavailableException(ErrorCode.EMAIL_DELIVERY_FAILED,
                "No se pudo entregar el código."));
        canonical(503, "EMAIL_DELIVERY_FAILED");
    }

    private void canonical(int status, String code) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/v1/mobile/auth/login")
                        .header("X-Correlation-ID", "l08-synthetic-http-case"))
                .andExpect(status().is(status)).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.timestamp").isNotEmpty()).andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/mobile/auth/login"))
                .andExpect(jsonPath("$.correlationId").value("l08-synthetic-http-case"))
                .andExpect(jsonPath("$.errors").isArray()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(SECRET, "jdbc:", "SQLTransient", "password");
        assertThat(events.list).allSatisfy(event->{
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET,"jdbc:");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
