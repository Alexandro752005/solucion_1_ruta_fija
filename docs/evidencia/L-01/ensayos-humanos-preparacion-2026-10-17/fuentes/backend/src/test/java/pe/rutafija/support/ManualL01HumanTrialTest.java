package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ManualL01HumanTrialTest {
    @TempDir Path directory;

    @Test void runtimeKeepsBothGatesOfflineDisabledAndNoMigrations() {
        var p = ManualL01HumanTrialServer.runtimeProperties("SYNTHETIC-ONLY");
        assertThat(p).containsEntry("app.security.admin-terms-gate.enabled", true)
                .containsEntry("app.security.person-identity-gate.enabled", true)
                .containsEntry("spring.flyway.enabled", false)
                .containsEntry("spring.jpa.hibernate.ddl-auto", "validate")
                .containsEntry("app.mobile.offline-sync.enabled", false)
                .containsEntry("app.seed.enabled", false)
                .containsEntry("spring.profiles.active", "test");
    }

    @Test void mailAutoconfigurationAndCredentialFallbackAreDisabled() {
        var p = ManualL01HumanTrialServer.runtimeProperties("SYNTHETIC-ONLY");
        assertThat(p).containsEntry("spring.mail.host", "")
                .containsEntry("spring.mail.username", "")
                .containsEntry("spring.mail.password", "")
                .containsEntry("spring.config.import", "");
        assertThat(p.get("spring.autoconfigure.exclude").toString()).contains("MailSenderAutoConfiguration");
    }

    @Test void bothServersAreLoopbackAndOnlyFrontendOriginIsAccepted() {
        var p = ManualL01HumanTrialServer.runtimeProperties("SYNTHETIC-ONLY");
        assertThat(p).containsEntry("server.address", "127.0.0.1")
                .containsEntry("server.forward-headers-strategy", "none")
                .containsEntry("app.security.cors.allowed-origins", "http://127.0.0.1:4311");
    }

    @Test void ephemeralSigningKeysAreStrongAndDifferent() {
        String a = ManualL01HumanTrialServer.randomSigningKey(), b = ManualL01HumanTrialServer.randomSigningKey();
        assertThat(Base64.getDecoder().decode(a)).hasSize(64);
        assertThat(a.equals(b)).isFalse();
    }

    @Test void privateRuntimeFileNeverOverwritesExistingContent() throws Exception {
        Path file = directory.resolve("synthetic.private");
        ManualL01HumanTrialServer.writeRestricted(file, "SYNTHETIC_TEST_DATA".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> ManualL01HumanTrialServer.writeRestricted(file, new byte[8])).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(file)).isEqualTo("SYNTHETIC_TEST_DATA");
    }

    @Test void publishedApprovedDocumentIsReadWithoutMutation() throws Exception {
        assertThat(ManualL01HumanTrial.publishedHashMatches()).isTrue();
        assertThat(ManualL01HumanTrial.APPROVED_SHA).isEqualTo("20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417");
    }

    @Test void pendingHumanTrialBlocksCleanupWithoutConnectingToDatabase() throws Exception {
        Path marker = directory.resolve("l01-human-trial.active");
        NativePostgresTestDatabase.assertNoHumanTrialMarker(marker);
        Files.writeString(marker, UUID.randomUUID().toString());
        assertThatThrownBy(() -> NativePostgresTestDatabase.assertNoHumanTrialMarker(marker))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("limpieza");
        assertThat(Files.exists(marker)).isTrue();
    }

    @Test void adminEvidenceRequiresEveryHumanHttpAndPersistedPrecondition() {
        Map<String,Object> state=new HashMap<>();
        state.put("preAcceptanceGate",Map.of("passed",true));
        var required=List.of("adminCreatedInactiveObserved","explicitAcceptanceHttpObserved","activatedAdminLoginObserved","humanAdminConfirmation");
        required.forEach(k->state.put(k,true));
        Map<String,Object> row=new HashMap<>(Map.of("status","COMPLETED","active",true,"ordering_valid",true,"identity_reviewed",true,"acceptance_audited",true));
        assertThat(ManualL01HumanTrialServer.adminEvidenceComplete(state,List.of(row))).isTrue();
        for(String key:required){state.put(key,false);assertThat(ManualL01HumanTrialServer.adminEvidenceComplete(state,List.of(row))).isFalse();state.put(key,true);}
        row.put("ordering_valid",false);
        assertThat(ManualL01HumanTrialServer.adminEvidenceComplete(state,List.of(row))).isFalse();
        assertThat(ManualL01HumanTrialServer.adminEvidenceComplete(state,List.of())).isFalse();
    }

    @Test void recoveryEvidenceCannotReplaceLocalCopyOrHumanActionWithAutomaticResults() {
        Map<String,Object> state=new HashMap<>();
        var required=List.of("humanRecoveryConfirmation","humanTemporaryCopyReadAndUsedReported","publicKeyPreserved","oldAccessSessionRejected",
                "oldRefreshSessionRejected","oldEmergencyCodeRejectedByStoredHash","rotatedEmergencyCodeMatchesStoredHash","recoveredOwnerLoginObserved","sessionVersionIncremented");
        required.forEach(k->state.put(k,true));state.put("enrollmentResponseCount",1);state.put("recoveryCompletionResponseCount",1);
        assertThat(ManualL01HumanTrialServer.recoveryEvidenceComplete(state)).isTrue();
        for(String key:required){state.put(key,false);assertThat(ManualL01HumanTrialServer.recoveryEvidenceComplete(state)).isFalse();state.put(key,true);}
        state.put("recoveryCompletionResponseCount",2);
        assertThat(ManualL01HumanTrialServer.recoveryEvidenceComplete(state)).isFalse();
    }

    @Test void observationFailureNeverProducesSuccessfulManualResults() {
        assertThat(ManualL01HumanTrialServer.adminEvidenceComplete(Map.of("observationIncomplete",true),List.of())).isFalse();
        assertThat(ManualL01HumanTrialServer.recoveryEvidenceComplete(Map.of("observationIncomplete",true))).isFalse();
    }
}
