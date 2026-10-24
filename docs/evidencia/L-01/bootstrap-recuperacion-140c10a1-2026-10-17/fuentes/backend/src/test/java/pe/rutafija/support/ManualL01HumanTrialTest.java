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

    private Map<String,Object> emptyBootstrapInventory() {
        Map<String,Object> inventory=new HashMap<>();
        for(String key:List.of("superAdminAccounts","superAdminHistoricalCreations","caseAccounts","caseOrganizations","identityBindings","identityKeyRecords","recoveryCredentials")) inventory.put(key,0L);
        inventory.put("adminHistoricalCreations",1L);
        return inventory;
    }

    @Test void legacyMarkerCanResumeOnlyTheSameEmptyUnpreparedCase() {
        UUID run=UUID.randomUUID();
        assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,run.toString(),Map.of(),false,emptyBootstrapInventory())).isNull();
        assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,UUID.randomUUID().toString(),Map.of(),false,emptyBootstrapInventory()))
                .isEqualTo("RESUME_MARKER_ID_MISMATCH");
        assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,"",Map.of(),false,emptyBootstrapInventory()))
                .isEqualTo("RESUME_MARKER_ID_MISMATCH");
    }

    @Test void resumeRejectsForeignOrWrongDatabaseState() {
        UUID run=UUID.randomUUID();
        var wrongRun=Map.<String,Object>of("runId",UUID.randomUUID().toString(),"database","ruta_fija_test");
        var wrongDatabase=Map.<String,Object>of("runId",run.toString(),"database","UNAUTHORIZED_TEST_LABEL");
        for(var state:List.of(wrongRun,wrongDatabase)) assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,run.toString(),state,false,emptyBootstrapInventory()))
                .isEqualTo("RESUME_CASE_STATE_MISMATCH");
    }

    @Test void everyPersistedIdentityLedgerOrganizationOrKeyPreventsPasswordRecapture() {
        UUID run=UUID.randomUUID();
        for(String key:emptyBootstrapInventory().keySet()) {
            if ("adminHistoricalCreations".equals(key)) continue;
            Map<String,Object> inventory=emptyBootstrapInventory();inventory.put(key,1L);
            assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,run.toString(),Map.of(),false,inventory))
                    .isEqualTo("BOOTSTRAP_PERSISTENCE_NOT_EMPTY_NO_PASSWORD_RECAPTURE");
        }
    }

    @Test void preparedCredentialsNeverPermitAReplacementPasswordByInference() {
        UUID run=UUID.randomUUID();
        Map<String,Object> state=new HashMap<>(Map.of("runId",run.toString(),"database","ruta_fija_test"));
        assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,run.toString(),state,true,emptyBootstrapInventory()))
                .isEqualTo("PREPARED_CREDENTIALS_REQUIRE_REVIEW_NO_PASSWORD_OVERWRITE");
        for(String key:List.of("ownerId","organizationId")) {
            var prepared=new HashMap<>(state);prepared.put(key,UUID.randomUUID().toString());
            assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,run.toString(),prepared,false,emptyBootstrapInventory()))
                    .isEqualTo("PREPARED_CREDENTIALS_REQUIRE_REVIEW_NO_PASSWORD_OVERWRITE");
        }
    }

    @Test void pendingCasePreservesItsIdAndHistoryDuringEligibilityCheck() {
        UUID run=UUID.randomUUID();
        Map<String,Object> state=new HashMap<>(Map.of("runId",run.toString(),"database","ruta_fija_test",
                "bootstrapStage","PENDING_PASSWORD","bootstrapHistory",List.of(Map.of("event","PASSWORD_CAPTURE_REJECTED","reason","PASSWORD_CONFIRMATION_MISMATCH"))));
        var original=new HashMap<>(state);
        assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,run.toString(),state,false,emptyBootstrapInventory())).isNull();
        assertThat(state).isEqualTo(original);
    }

    @Test void asciiPasswordBoundariesRemainTwelveThroughSeventyTwo() {
        for(int length:List.of(12,64,72)) {
            char[] synthetic="X".repeat(length).toCharArray();
            assertThat(ManualL01HumanTrialServer.passwordCaptureRefusal(synthetic,synthetic.clone())).isNull();
        }
        for(int length:List.of(0,11,73)) {
            char[] synthetic="X".repeat(length).toCharArray();
            assertThat(ManualL01HumanTrialServer.passwordCaptureRefusal(synthetic,synthetic.clone())).isEqualTo("PASSWORD_LENGTH_INVALID");
        }
    }

    @Test void futurePasswordErrorsHaveSeparateSanitizedReasons() {
        assertThat(ManualL01HumanTrialServer.passwordCaptureRefusal("SYNTHETIC-ONE".toCharArray(),"SYNTHETIC-TWO".toCharArray()))
                .isEqualTo("PASSWORD_CONFIRMATION_MISMATCH");
        assertThat(ManualL01HumanTrialServer.passwordCaptureRefusal(null,new char[12])).isEqualTo("PASSWORD_INPUT_CANCELLED");
        assertThat(ManualL01HumanTrialServer.passwordCaptureRefusal(new char[12],null)).isEqualTo("PASSWORD_INPUT_CANCELLED");
        char[] tooManyBytes="é".repeat(37).toCharArray();
        assertThat(ManualL01HumanTrialServer.passwordCaptureRefusal(tooManyBytes,tooManyBytes.clone())).isEqualTo("PASSWORD_UTF8_LIMIT_EXCEEDED");
        char[] exactBytes="é".repeat(36).toCharArray();
        assertThat(ManualL01HumanTrialServer.passwordCaptureRefusal(exactBytes,exactBytes.clone())).isNull();
    }

    @Test void publicCaseStateIsReplacedAtomicallyAndKeepsHistory() throws Exception {
        Path state=directory.resolve("public-state.json");
        UUID run=UUID.randomUUID();
        Map<String,Object> data=new HashMap<>(Map.of("runId",run.toString(),"bootstrapHistory",List.of(Map.of("event","PASSWORD_CAPTURE_STARTED"))));
        ManualL01HumanTrialServer.writePublicStateAtomically(state,data);
        data.put("bootstrapStage","PENDING_PASSWORD");
        ManualL01HumanTrialServer.writePublicStateAtomically(state,data);
        var stored=ManualL01HumanTrial.JSON.readTree(Files.readAllBytes(state));
        assertThat(stored.path("runId").asText()).isEqualTo(run.toString());
        assertThat(stored.path("bootstrapHistory").size()).isEqualTo(1);
        try(var files=Files.list(directory)){assertThat(files.filter(p->p.getFileName().toString().endsWith(".tmp")).count()).isZero();}
    }

    @Test void resumeAlsoRetainsTheAdminHistoricalCreationLimit() {
        UUID run=UUID.randomUUID();
        var inventory=emptyBootstrapInventory();
        inventory.put("adminHistoricalCreations",4L);
        assertThat(ManualL01HumanTrialServer.bootstrapResumeRefusal(run,run.toString(),Map.of(),false,inventory))
                .isEqualTo("HISTORICAL_QUOTA_EXHAUSTED");
    }
}
