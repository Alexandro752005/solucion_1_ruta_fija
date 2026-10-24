package pe.rutafija.audit.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import pe.rutafija.audit.domain.AuditEvent;
import pe.rutafija.audit.infrastructure.AuditEventRepository;
import pe.rutafija.shared.observability.CorrelationIdFilter;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AuditOutcomeTest {
    private AuditEvent record(String action){var repository=mock(AuditEventRepository.class);var service=new AuditService(repository,Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC),new AuditMetadataPolicy());service.record(null,action,Map.of("auditOutcome","SUCCEEDED"));var argument=ArgumentCaptor.forClass(AuditEvent.class);verify(repository).save(argument.capture());return argument.getValue();}
    @ParameterizedTest @ValueSource(strings={"SECURITY_ACCESS_DENIED","LOGIN_FAILURE","MOBILE_LOGIN_DENIED","REAUTH_FAILED","REFRESH_REUSE_DETECTED","MOBILE_REFRESH_REUSE_DETECTED","SUPER_ADMIN_RECOVERY_PROOF_REJECTED"})
    void denialOutcomeComesFromServerAction(String action){assertThat(record(action).getMetadata()).containsEntry("auditOutcome","DENIED");}
    @Test void ordinaryBusinessRejectionIsASuccessfullyRecordedDecision(){assertThat(record("CONDUCTOR_ONBOARDING_REJECTED").getMetadata()).containsEntry("auditOutcome","SUCCEEDED");}
    @Test void arbitraryCorrelationContentIsNeverPersisted(){MDC.put(CorrelationIdFilter.MDC_KEY,"FAKE_CORRELATION_INPUT");try{assertThat(record("L07_TEST").getCorrelationId()).isNull();}finally{MDC.remove(CorrelationIdFilter.MDC_KEY);}}
    @Test void publicUuidCorrelationIsPreserved(){String id=UUID.randomUUID().toString();MDC.put(CorrelationIdFilter.MDC_KEY,id);try{assertThat(record("L07_TEST").getCorrelationId()).isEqualTo(id);}finally{MDC.remove(CorrelationIdFilter.MDC_KEY);}}
    @Test void invalidLegacyCorrelationIsHiddenAndDoesNotBecomeAnotherIdentifier(){assertThat(AuditCorrelationId.safe(null)).isNull();assertThat(AuditCorrelationId.safe("synthetic@example.test")).isNull();assertThat(AuditCorrelationId.safe("0-0-0-0-0")).isNull();}
}
