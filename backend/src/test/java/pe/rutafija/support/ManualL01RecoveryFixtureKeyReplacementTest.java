package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static pe.rutafija.support.ManualL01RecoveryFixtureKeyReplacement.*;

/** Only unit doubles and ephemeral generated public keys; never the human fixture or private files. */
class ManualL01RecoveryFixtureKeyReplacementTest {
    static final UUID SEGMENT = UUID.fromString("d989784c-0e1c-4143-97db-8bd518dfe3c3");
    static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    static PublicCandidate synthetic() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        return candidate(encoded, fingerprint(encoded));
    }
    static Map<String, Object> ready() {
        var facts = new LinkedHashMap<String, Object>(); REQUIRED.forEach(key -> facts.put(key, true)); return facts;
    }
    static class FakeStore implements Store {
        final Map<String, Object> facts = ready();
        final AtomicInteger commits = new AtomicInteger();
        final List<String> sequence;
        PublicCandidate current;
        FakeStore(List<String> sequence) { this.sequence = sequence; }
        public synchronized Map<String, Object> inspect() {
            sequence.add("inspect"); var result = new LinkedHashMap<>(facts);
            if (commits.get() > 0) result.put("noPriorKeyReplacement", false); return result;
        }
        public synchronized Map<String, Object> commit(PublicCandidate key) {
            requireReady(inspect()); current = key; commits.incrementAndGet(); sequence.add("commit");
            return publicResult(UUID.randomUUID().toString(), NOW, SEGMENT.toString(), key.fingerprint());
        }
    }
    static class FakeTerminal implements PublicTerminal {
        final List<String> sequence;
        final PublicCandidate key;
        Confirmation confirmation = new Confirmation(true, true, true);
        Runnable beforeConfirmation = () -> { };
        FakeTerminal(List<String> sequence, PublicCandidate key) { this.sequence = sequence; this.key = key; }
        public String readPublicKey() { sequence.add("public"); return key.encoded(); }
        public String readFingerprint() { sequence.add("fingerprint"); return key.fingerprint(); }
        public Confirmation confirm(PublicCandidate candidate) { sequence.add("confirm"); beforeConfirmation.run(); return confirmation; }
    }
    static class FakeAuthentication implements Authentication {
        final List<String> sequence;
        String failingPhase;
        FakeAuthentication(List<String> sequence) { this.sequence = sequence; }
        void step(String phase) { sequence.add(phase); if (phase.equals(failingPhase)) throw new IllegalStateException("KEY_PREP_"+phase.toUpperCase(Locale.ROOT)+"_REFUSED"); }
        public void authenticate() { step("authenticate"); }
        public void reauthenticate() { step("reauthenticate"); }
        public void verifyCurrentSession() { step("session"); }
    }

    @Test void exactApprovalAndFrozenProposalMatchTheirHashes() throws Exception {
        assertThat(authorizationValid()).isTrue();
    }
    @Test void everyGuardFailsClosedBeforeAuthenticationOrPublicInput() throws Exception {
        PublicCandidate key = synthetic();
        for (String guard : REQUIRED) {
            var sequence = new ArrayList<String>(); var store = new FakeStore(sequence); store.facts.put(guard, false);
            assertThatThrownBy(() -> execute(new FakeTerminal(sequence, key), new FakeAuthentication(sequence), store))
                    .hasMessageStartingWith("KEY_PREP_GUARD_");
            assertThat(sequence).containsExactly("inspect"); assertThat(store.commits.get()).isZero();
        }
    }
    @Test void missingGuardIsNotAcceptedAsTrue() {
        var facts = ready(); facts.remove("authorization");
        assertThat(refusal(facts)).isEqualTo("KEY_PREP_GUARD_AUTHORIZATION");
    }
    @Test void acceptsOnlyCanonicalEd25519PublicKeyWithMatchingNewFingerprint() throws Exception {
        PublicCandidate key = synthetic();
        assertThat(candidate(key.encoded(), key.fingerprint().toUpperCase(Locale.ROOT))).isEqualTo(key);
        for (String bad : List.of("", "not-base64", Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(new byte[44]), key.encoded()+"=", key.encoded()+"\nINVALID"))
            assertThatThrownBy(() -> candidate(bad, key.fingerprint())).hasMessage("KEY_PREP_PUBLIC_OR_FINGERPRINT_INVALID");
        assertThatThrownBy(() -> candidate(null, key.fingerprint())).hasMessage("KEY_PREP_PUBLIC_OR_FINGERPRINT_INVALID");
        for (String hash : List.of("", "0".repeat(64), "G".repeat(64), ManualL01RecoveryFixtureRepair.PUBLIC_SHA))
            assertThatThrownBy(() -> candidate(key.encoded(), hash)).hasMessage("KEY_PREP_PUBLIC_OR_FINGERPRINT_INVALID");
    }
    @Test void authenticationReauthenticationAndConfirmationPrecedeCommit() throws Exception {
        var sequence = new ArrayList<String>(); var store = new FakeStore(sequence); var key = synthetic();
        Map<String, Object> result = execute(new FakeTerminal(sequence, key), new FakeAuthentication(sequence), store);
        assertThat(sequence).containsExactly("inspect", "authenticate", "reauthenticate", "public", "fingerprint", "confirm", "session", "inspect", "inspect", "commit");
        assertThat(result).containsEntry("originalEnrollmentResponseCount",1).containsEntry("syntheticFixtureKeyReplacementCount",1)
                .containsEntry("fixtureKeyPreparationSource",SOURCE).containsEntry("currentRecoveryKeyFingerprintSha256",key.fingerprint());
        assertThat(result).doesNotContainKeys("enrollmentResponseCount", "manualTemporaryRecoveryResult", "humanRecoveryConfirmation",
                "emergencyCode", "emergency_code_hash", "password", "privateKey", "privatePath");
        assertThat(store.current).isEqualTo(key); assertThat(store.commits.get()).isEqualTo(1);
    }
    @Test void eachAuthenticationPhaseCanRefuseWithoutAnyWrite() throws Exception {
        var key = synthetic();
        for (String phase : List.of("authenticate", "reauthenticate", "session")) {
            var sequence = new ArrayList<String>(); var store = new FakeStore(sequence); var auth = new FakeAuthentication(sequence); auth.failingPhase = phase;
            assertThatThrownBy(() -> execute(new FakeTerminal(sequence,key),auth,store)).hasMessageStartingWith("KEY_PREP_");
            assertThat(store.commits.get()).isZero();
            if (!phase.equals("session")) assertThat(sequence).doesNotContain("public", "confirm");
        }
    }
    @Test void cancelOrEachMissingHumanConfirmationRefusesBeforeWriting() throws Exception {
        var key = synthetic();
        for (Confirmation confirmation : Arrays.asList(null,new Confirmation(false,true,true),new Confirmation(true,false,true),new Confirmation(true,true,false))) {
            var sequence = new ArrayList<String>(); var store = new FakeStore(sequence); var terminal = new FakeTerminal(sequence,key); terminal.confirmation = confirmation;
            assertThatThrownBy(() -> execute(terminal,new FakeAuthentication(sequence),store)).hasMessage("KEY_PREP_CONFIRMATION_REJECTED_NO_WRITE");
            assertThat(store.commits.get()).isZero(); assertThat(sequence).doesNotContain("session");
        }
    }
    @Test void stateChangedDuringConfirmationIsCheckedAgain() throws Exception {
        var sequence = new ArrayList<String>(); var store = new FakeStore(sequence); var terminal = new FakeTerminal(sequence,synthetic());
        terminal.beforeConfirmation = () -> store.facts.put("noLiveChallenge",false);
        assertThatThrownBy(() -> execute(terminal,new FakeAuthentication(sequence),store)).hasMessage("KEY_PREP_GUARD_NO_LIVE_CHALLENGE");
        assertThat(store.commits.get()).isZero();
    }
    @Test void successfulReplacementCannotBeRepeated() throws Exception {
        var sequence = new ArrayList<String>(); var store = new FakeStore(sequence); var key = synthetic();
        execute(new FakeTerminal(sequence,key),new FakeAuthentication(sequence),store);
        assertThatThrownBy(() -> execute(new FakeTerminal(sequence,key),new FakeAuthentication(sequence),store))
                .hasMessage("KEY_PREP_GUARD_NO_PRIOR_KEY_REPLACEMENT");
        assertThat(store.commits.get()).isEqualTo(1);
    }
    @Test void concurrentConfirmationsCommitAtMostOnceInGuardedDouble() throws Exception {
        var sequence = Collections.synchronizedList(new ArrayList<String>()); var store = new FakeStore(sequence); var key = synthetic();
        var both = new CountDownLatch(2);
        Callable<Boolean> attempt = () -> {
            var terminal = new FakeTerminal(sequence,key);
            terminal.beforeConfirmation = () -> { both.countDown(); try { if (!both.await(5,TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(); } };
            try { execute(terminal,new FakeAuthentication(sequence),store); return true; } catch (IllegalStateException refused) { return false; }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(attempt); var b = executor.submit(attempt);
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(store.commits.get()).isEqualTo(1);
    }

    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        @java.io.Serial
        private static final long serialVersionUID = -7780495166934619831L;
        int commits,rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction,TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
    static class RecordingJdbc extends JdbcTemplate {
        final List<String> sql = new ArrayList<>();
        String metadata; boolean failAudit; boolean refuseUpdate;
        public List<Map<String,Object>> queryForList(String statement) { return queryForList(statement,new Object[0]); }
        public List<Map<String,Object>> queryForList(String statement,Object... args) { sql.add(statement); return List.of(); }
        public int update(String statement,Object... args) {
            sql.add(statement);
            if (statement.stripLeading().startsWith("insert")) { metadata=args[4].toString(); if(failAudit)throw new IllegalStateException("KEY_PREP_AUDIT_REFUSED"); }
            if (statement.stripLeading().startsWith("update") && refuseUpdate) return 0;
            return 1;
        }
        public <T>T queryForObject(String statement,Class<T> type,Object... args) {
            sql.add(statement);
            return type.cast(type==String.class ? "ORIGINAL_PUBLIC_X509_TEST_ONLY" : Timestamp.from(NOW));
        }
        public <T>T queryForObject(String statement,Class<T> type) { return queryForObject(statement,type,new Object[0]); }
    }
    static JdbcStore jdbcStore(RecordingJdbc jdbc,RecordingTransactions tx) {
        return new JdbcStore(jdbc,tx,Map.of(),1,SEGMENT) { public Map<String,Object> inspect() { return ready(); } };
    }
    @Test void transactionChangesOnlyPublicKeyAndTimestampAndWritesPublicAudit() throws Exception {
        var jdbc = new RecordingJdbc(); var tx = new RecordingTransactions(); var key = synthetic();
        var result = jdbcStore(jdbc,tx).commit(key);
        assertThat(tx.commits).isEqualTo(1); assertThat(tx.rollbacks).isZero();
        assertThat(jdbc.sql.stream().filter(s->s.contains("pg_advisory_xact_lock(751844,?)")).count()).isEqualTo(3);
        assertThat(jdbc.sql).contains(UPDATE_SQL,AUDIT_SQL).anyMatch(s->s.contains("for update"));
        assertThat(UPDATE_SQL).doesNotContain("emergency", "password", "email=", "delete", "enrolled_at=");
        // user_id appears only in WHERE; ensure the SET clause is exactly the authorized fields.
        assertThat(UPDATE_SQL.split("where")[0].strip()).isEqualTo("update super_admin_recovery_credential set public_key_x509=?,updated_at=current_timestamp");
        assertThat(jdbc.metadata).contains("TEMPORARY_KEY_ENCRYPTION_PASSWORD_UNAVAILABLE",key.fingerprint(),SOURCE)
                .doesNotContain("emergency_code_hash", "privateKey", "passwordValue", "privatePath");
        assertThat(result).doesNotContainKeys("enrollmentResponseCount","manualTemporaryRecoveryResult");
        assertThat(jdbc.sql).noneMatch(s->s.contains("lock table pilot_account_creation") || s.startsWith("delete"));
    }
    @Test void auditFailureRollsBackTheSameTransaction() throws Exception {
        var jdbc = new RecordingJdbc();jdbc.failAudit=true;var tx = new RecordingTransactions();var key=synthetic();
        assertThatThrownBy(() -> jdbcStore(jdbc,tx).commit(key)).hasMessage("KEY_PREP_AUDIT_REFUSED");
        assertThat(tx.commits).isZero();assertThat(tx.rollbacks).isEqualTo(1);
    }
    @Test void conditionalUpdateRefusalDoesNotInsertAudit() throws Exception {
        var jdbc = new RecordingJdbc();jdbc.refuseUpdate=true;var tx = new RecordingTransactions();var key=synthetic();
        assertThatThrownBy(() -> jdbcStore(jdbc,tx).commit(key)).hasMessage("KEY_PREP_UPDATE_REFUSED");
        assertThat(jdbc.metadata).isNull();assertThat(tx.rollbacks).isEqualTo(1);
    }
    @Test void rowLockGuardIsRepeatedBeforeAnyUpdate() throws Exception {
        var jdbc = new RecordingJdbc();var tx = new RecordingTransactions();var key=synthetic();
        var store=new JdbcStore(jdbc,tx,Map.of(),1,SEGMENT) { public Map<String,Object> inspect() { var f=ready();f.put("noPriorKeyReplacement",false);return f; } };
        assertThatThrownBy(() -> store.commit(key)).hasMessage("KEY_PREP_GUARD_NO_PRIOR_KEY_REPLACEMENT");
        assertThat(jdbc.sql).noneMatch(s->s.stripLeading().startsWith("update") || s.stripLeading().startsWith("insert"));
        assertThat(tx.rollbacks).isEqualTo(1);
    }
    @Test void directStoreStillValidatesCandidate() {
        var jdbc = new RecordingJdbc();var tx = new RecordingTransactions();
        assertThatThrownBy(() -> jdbcStore(jdbc,tx).commit(new PublicCandidate("INVALID","0".repeat(64))))
                .hasMessage("KEY_PREP_PUBLIC_OR_FINGERPRINT_INVALID");
        assertThat(jdbc.sql).isEmpty();assertThat(tx.commits).isZero();
    }

    static Map<String,Object> auditRow(PublicCandidate key) {
        var row = new HashMap<String,Object>();
        row.put("audit_id",UUID.randomUUID().toString());row.put("owner_id",ManualL01RecoveryFixtureRepair.OWNER.toString());
        row.put("installation_id",ManualL01RecoveryFixtureRepair.INSTALLATION.toString());row.put("occurred_at",Timestamp.from(NOW));
        row.put("proposal_id",PROPOSAL_ID);row.put("proposal_sha256",PROPOSAL_SHA);row.put("approval_sha256",APPROVAL_SHA);
        row.put("case_run_id",ManualL01RecoveryFixtureRepair.RUN.toString());row.put("segment_id",SEGMENT.toString());
        row.put("old_public_sha",ManualL01RecoveryFixtureRepair.PUBLIC_SHA);row.put("new_public_sha",key.fingerprint());
        row.put("replacement_count","1");row.put("fingerprint_confirmed","true");row.put("pair_checks_reported","true");
        row.put("emergency_available_reported","true");row.put("preparation_source",SOURCE);return row;
    }
    @Test void restoredPublicAuditMustMatchAllExactReferencesAndCurrentKey() throws Exception {
        var key = synthetic();var row=auditRow(key);
        assertThat(restorePublicReference(row,key.fingerprint(),Map.of()))
                .containsEntry("originalEnrollmentResponseCount",1).containsEntry("currentRecoveryKeyFingerprintSha256",key.fingerprint())
                .containsEntry("fixtureKeyPreparationSource",SOURCE).doesNotContainKey("enrollmentResponseCount");
        for(String field:row.keySet()) {
            var wrong=new HashMap<>(row);wrong.put(field,"INVALID");
            assertThatThrownBy(()->restorePublicReference(wrong,key.fingerprint(),Map.of())).hasMessage("KEY_PREP_RESTORED_PUBLIC_REFERENCE_REFUSED");
        }
        assertThatThrownBy(()->restorePublicReference(row,"0".repeat(64),Map.of())).hasMessage("KEY_PREP_RESTORED_PUBLIC_REFERENCE_REFUSED");
        assertThatThrownBy(()->restorePublicReference(row,key.fingerprint(),Map.of("replacementPublicKeyFingerprintSha256","0".repeat(64))))
                .hasMessage("KEY_PREP_RESTORED_PUBLIC_REFERENCE_REFUSED");
    }
    @Test void replacementPreparationNeverByItselfProducesCompleteRecoveryEvidence() throws Exception {
        var key=synthetic();var state=publicResult(UUID.randomUUID().toString(),NOW,SEGMENT.toString(),key.fingerprint());
        assertThat(evidenceComplete(state)).isFalse();assertThat(ManualL01HumanTrialServer.recoveryEvidenceComplete(state)).isFalse();
        state.put("publicKeyPreservationBaselineSha256",key.fingerprint());state.put("recoveryCompletedPublicKeySha256",key.fingerprint());
        state.put("fixtureKeyPersistedAuditCorrespondenceVerified",true);
        state.put("syntheticFixtureCodeRepairCount",1);state.put("fixtureRepairAuditId",CODE_REPAIR_AUDIT.toString());
        assertThat(evidenceComplete(state)).isTrue();
        state.remove("syntheticFixtureCodeRepairCount");assertThat(evidenceComplete(state)).isFalse();state.put("syntheticFixtureCodeRepairCount",1);
        for(String field:List.of("fixtureKeyPersistedAuditCorrespondenceVerified","fixtureKeyHumanFingerprintConfirmationBeforeWrite",
                "fixtureKeyHumanPrimaryAndCopyChecksReported","fixtureKeyCurrentEmergencyCodeAvailableReported")) {
            state.put(field,false);assertThat(evidenceComplete(state)).isFalse();state.put(field,true);
        }
        state.put("recoveryCompletedPublicKeySha256",ManualL01RecoveryFixtureRepair.PUBLIC_SHA);
        assertThat(evidenceComplete(state)).isFalse();
        assertThat(ManualL01HumanTrialServer.recoveryEvidenceComplete(state)).isFalse();
    }
}
