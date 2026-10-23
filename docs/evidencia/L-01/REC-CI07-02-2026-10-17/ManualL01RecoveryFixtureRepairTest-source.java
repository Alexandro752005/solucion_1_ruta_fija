package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/** Unit doubles only: never loads Spring, database configuration, or the human fixture. */
class ManualL01RecoveryFixtureRepairTest {
    static Map<String, Object> ready() {
        Map<String, Object> facts = new LinkedHashMap<>();
        ManualL01RecoveryFixtureRepair.REQUIRED.forEach(key -> facts.put(key, true));
        return facts;
    }

    static final class FakeStore implements ManualL01RecoveryFixtureRepair.Store {
        final Map<String, Object> facts = ready();
        final AtomicInteger commits = new AtomicInteger();
        final List<String> sequence;
        String encoded;
        FakeStore(List<String> sequence) { this.sequence = sequence; }
        public synchronized Map<String, Object> inspect() {
            sequence.add("inspect");
            var copy = new LinkedHashMap<>(facts);
            if (commits.get() != 0) copy.put("noPriorRepair", false);
            return copy;
        }
        public synchronized Map<String, Object> commit(String hash) {
            ManualL01RecoveryFixtureRepair.requireReady(inspect());
            encoded = hash; commits.incrementAndGet(); sequence.add("commit");
            return Map.of("syntheticFixtureCodeRepairCount", 1);
        }
    }

    static final class FakeTerminal implements ManualL01RecoveryFixtureRepair.ProtectedTerminal {
        final List<String> sequence;
        char[] shown, confirmation;
        boolean mismatch, cancelled, empty;
        int shownCount;
        Runnable beforeConfirmation = () -> { };
        FakeTerminal(List<String> sequence) { this.sequence = sequence; }
        public void showOnce(char[] code) { shown = code.clone(); shownCount++; sequence.add("show"); }
        public char[] readHiddenConfirmation() {
            sequence.add("confirm"); beforeConfirmation.run();
            confirmation = cancelled ? null : empty ? new char[0] : mismatch ? "Y".repeat(43).toCharArray() : shown.clone();
            return confirmation;
        }
    }

    static final class FakeAuthentication implements ManualL01RecoveryFixtureRepair.Authentication {
        final List<String> sequence;
        boolean failAuthentication, failSession;
        FakeAuthentication(List<String> sequence) { this.sequence = sequence; }
        public void authenticateAndReauthenticate() {
            sequence.add("authenticate");
            if (failAuthentication) throw new IllegalStateException("REPAIR_AUTHENTICATION_REFUSED");
            sequence.add("reauthenticate");
        }
        public void verifyCurrentSession() {
            sequence.add("session");
            if (failSession) throw new IllegalStateException("REPAIR_SESSION_REFUSED");
        }
    }

    static final class FakeEncoder implements PasswordEncoder {
        final List<String> sequence;
        int calls;
        FakeEncoder(List<String> sequence) { this.sequence = sequence; }
        public String encode(CharSequence raw) { sequence.add("encode"); calls++; return "SYNTHETIC_ENCODED_VALUE"; }
        public boolean matches(CharSequence raw, String encoded) { return false; }
    }

    @Test void eachGuardFailsClosedBeforeAuthenticationOrCodeGeneration() {
        for (String guard : ManualL01RecoveryFixtureRepair.REQUIRED) {
            var sequence = new ArrayList<String>(); var store = new FakeStore(sequence);
            store.facts.put(guard, false);
            var terminal = new FakeTerminal(sequence); var encoder = new FakeEncoder(sequence);
            assertThatThrownBy(() -> ManualL01RecoveryFixtureRepair.execute(terminal, new FakeAuthentication(sequence), store,
                    encoder, chars -> fail("No committed secret"), () -> { fail("No code generation"); return null; }))
                    .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("REPAIR_GUARD_");
            assertThat(sequence).containsExactly("inspect");
            assertThat(store.commits.get()).isZero(); assertThat(terminal.shownCount).isZero();
        }
    }

    @Test void missingGuardIsNotEquivalentToTrue() {
        var facts = ready(); facts.remove("authorizationAndOutputCheck");
        assertThat(ManualL01RecoveryFixtureRepair.refusal(facts)).isEqualTo("REPAIR_GUARD_AUTHORIZATION_AND_OUTPUT_CHECK");
    }

    @Test void confirmationPrecedesEncodingAndAtomicWriteAndSecretsAreCleared() throws Exception {
        var sequence = new ArrayList<String>(); var store = new FakeStore(sequence);
        var terminal = new FakeTerminal(sequence); var authentication = new FakeAuthentication(sequence);
        var encoder = new FakeEncoder(sequence); char[] generated = "X".repeat(43).toCharArray();
        var result = ManualL01RecoveryFixtureRepair.execute(terminal, authentication, store, encoder,
                chars -> { assertThat(new String(chars)).isEqualTo("X".repeat(43)); sequence.add("committed-memory"); }, () -> generated);
        assertThat(sequence).containsExactly("inspect", "authenticate", "reauthenticate", "show", "confirm", "session",
                "inspect", "encode", "inspect", "commit", "committed-memory");
        assertThat(store.commits.get()).isEqualTo(1); assertThat(terminal.shownCount).isEqualTo(1);
        assertThat(encoder.calls).isEqualTo(1); assertThat(store.encoded).isEqualTo("SYNTHETIC_ENCODED_VALUE");
        assertThat(result).containsEntry("syntheticFixtureCodeRepairCount", 1);
        assertThat(new String(generated)).isEqualTo("\0".repeat(43));
        assertThat(new String(terminal.confirmation)).isEqualTo("\0".repeat(43));
    }

    @Test void emptyCancelledOrDifferentConfirmationNeverEncodesOrWrites() {
        for (String kind : List.of("empty", "cancelled", "different")) {
            var sequence = new ArrayList<String>(); var store = new FakeStore(sequence);
            var terminal = new FakeTerminal(sequence); var encoder = new FakeEncoder(sequence);
            terminal.empty = kind.equals("empty"); terminal.cancelled = kind.equals("cancelled"); terminal.mismatch = kind.equals("different");
            char[] generated = "X".repeat(43).toCharArray();
            assertThatThrownBy(() -> ManualL01RecoveryFixtureRepair.execute(terminal, new FakeAuthentication(sequence), store, encoder,
                    chars -> fail("No committed value"), () -> generated)).hasMessage("REPAIR_CONFIRMATION_REJECTED_NO_WRITE");
            assertThat(store.commits.get()).isZero(); assertThat(encoder.calls).isZero();
            assertThat(new String(generated)).isEqualTo("\0".repeat(43));
        }
    }

    @Test void authenticationFailureShowsNothingAndGeneratesNothing() {
        var sequence = new ArrayList<String>(); var auth = new FakeAuthentication(sequence); auth.failAuthentication = true;
        var terminal = new FakeTerminal(sequence);
        assertThatThrownBy(() -> ManualL01RecoveryFixtureRepair.execute(terminal, auth, new FakeStore(sequence), new FakeEncoder(sequence),
                chars -> fail("No write"), () -> { fail("No generation"); return null; })).hasMessage("REPAIR_AUTHENTICATION_REFUSED");
        assertThat(terminal.shownCount).isZero();
    }

    @Test void sessionFailureAfterConfirmationDoesNotWrite() {
        var sequence = new ArrayList<String>(); var auth = new FakeAuthentication(sequence); auth.failSession = true;
        var store = new FakeStore(sequence); var encoder = new FakeEncoder(sequence);
        assertThatThrownBy(() -> ManualL01RecoveryFixtureRepair.execute(new FakeTerminal(sequence), auth, store, encoder,
                chars -> fail("No write"), () -> "X".repeat(43).toCharArray())).hasMessage("REPAIR_SESSION_REFUSED");
        assertThat(store.commits.get()).isZero(); assertThat(encoder.calls).isZero();
    }

    @Test void stateChangedDuringHumanConfirmationIsRecheckedBeforeWrite() {
        var sequence = new ArrayList<String>(); var store = new FakeStore(sequence); var terminal = new FakeTerminal(sequence);
        terminal.beforeConfirmation = () -> store.facts.put("noLiveChallenge", false);
        assertThatThrownBy(() -> ManualL01RecoveryFixtureRepair.execute(terminal, new FakeAuthentication(sequence), store,
                new FakeEncoder(sequence), chars -> fail("No write"), () -> "X".repeat(43).toCharArray()))
                .hasMessage("REPAIR_GUARD_NO_LIVE_CHALLENGE");
        assertThat(store.commits.get()).isZero();
    }

    @Test void successfulRepairCannotBeRepeated() throws Exception {
        var sequence = new ArrayList<String>(); var store = new FakeStore(sequence);
        ManualL01RecoveryFixtureRepair.execute(new FakeTerminal(sequence), new FakeAuthentication(sequence), store,
                new FakeEncoder(sequence), chars -> { }, () -> "X".repeat(43).toCharArray());
        var second = new FakeTerminal(sequence);
        assertThatThrownBy(() -> ManualL01RecoveryFixtureRepair.execute(second, new FakeAuthentication(sequence), store,
                new FakeEncoder(sequence), chars -> fail("No second write"), () -> "X".repeat(43).toCharArray()))
                .hasMessage("REPAIR_GUARD_NO_PRIOR_REPAIR");
        assertThat(second.shownCount).isZero(); assertThat(store.commits.get()).isEqualTo(1);
    }

    @Test void concurrentConfirmationsStillCommitAtMostOnceInTheGuardedStore() throws Exception {
        var sequence = Collections.synchronizedList(new ArrayList<String>()); var store = new FakeStore(sequence);
        var bothConfirmed = new CountDownLatch(2);
        Callable<Boolean> work = () -> {
            var terminal = new FakeTerminal(sequence);
            terminal.beforeConfirmation = () -> { bothConfirmed.countDown(); try { if (!bothConfirmed.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(); } };
            try {
                ManualL01RecoveryFixtureRepair.execute(terminal, new FakeAuthentication(sequence), store,
                        new FakeEncoder(sequence), chars -> { }, () -> "X".repeat(43).toCharArray()); return true;
            } catch (IllegalStateException expected) { return false; }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(work); var b = executor.submit(work);
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(store.commits.get()).isEqualTo(1);
    }

    @Test void formatRejectsWrongLengthAlphabetAndNull() {
        assertThat(ManualL01RecoveryFixtureRepair.validCode("X".repeat(43).toCharArray())).isTrue();
        for (String value : List.of("X".repeat(42), "X".repeat(44), " ".repeat(43), "é".repeat(43)))
            assertThat(ManualL01RecoveryFixtureRepair.validCode(value.toCharArray())).isFalse();
        assertThat(ManualL01RecoveryFixtureRepair.validCode(null)).isFalse();
    }

    @Test void approvedFilesAndConsoleConditionMatchTheirExactHashes() throws Exception {
        assertThat(ManualL01RecoveryFixtureRepair.authorizationValid()).isTrue();
    }

    static final class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    static final class RecordingJdbc extends JdbcTemplate {
        final List<String> sql = new ArrayList<>();
        boolean failAudit;
        String metadata;
        public List<Map<String, Object>> queryForList(String statement) { return queryForList(statement, new Object[0]); }
        public List<Map<String, Object>> queryForList(String statement, Object... args) { sql.add(statement); return List.of(); }
        public void execute(String statement) { sql.add(statement); }
        public int update(String statement, Object... args) {
            sql.add(statement);
            if (statement.stripLeading().startsWith("insert")) {
                metadata = args[4].toString();
                if (failAudit) throw new IllegalStateException("REPAIR_AUDIT_REFUSED");
            }
            return 1;
        }
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            return type.cast(Timestamp.from(Instant.parse("2026-10-17T00:00:00Z")));
        }
    }

    @Test void auditAndCodeUpdateShareOneTransactionAndExposeOnlyPublicMetadata() throws Exception {
        var jdbc = new RecordingJdbc(); var transactions = new RecordingTransactions();
        var store = new ManualL01RecoveryFixtureRepair.JdbcStore(jdbc, transactions, Map.of(), 1, UUID.randomUUID()) {
            public Map<String, Object> inspect() { return ready(); }
        };
        var result = store.commit("SYNTHETIC_ENCODED_VALUE");
        assertThat(transactions.commits).isEqualTo(1); assertThat(transactions.rollbacks).isZero();
        assertThat(jdbc.sql).anyMatch(sql -> sql.contains("for update"));
        assertThat(jdbc.sql.stream().filter(sql -> sql.contains("pg_advisory_xact_lock(751844,?)")).count()).isEqualTo(3);
        assertThat(jdbc.sql).noneMatch(sql -> sql.contains("lock table pilot_account_creation"));
        assertThat(jdbc.metadata).contains("EMERGENCY_CODE_DELIVERY_NOT_ACCREDITED", "syntheticFixtureCodeRepairCount")
                .doesNotContain("SYNTHETIC_ENCODED_VALUE", "emergency_code_hash");
        assertThat(result.toString()).doesNotContain("SYNTHETIC_ENCODED_VALUE");
    }

    @Test void auditFailureRollsBackTheSameTransactionAsTheUpdate() {
        var jdbc = new RecordingJdbc(); jdbc.failAudit = true; var transactions = new RecordingTransactions();
        var store = new ManualL01RecoveryFixtureRepair.JdbcStore(jdbc, transactions, Map.of(), 1, UUID.randomUUID()) {
            public Map<String, Object> inspect() { return ready(); }
        };
        assertThatThrownBy(() -> store.commit("SYNTHETIC_ENCODED_VALUE")).hasMessage("REPAIR_AUDIT_REFUSED");
        assertThat(transactions.commits).isZero(); assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test void guardIsRepeatedAfterRowLocksBeforeAnyUpdate() {
        var jdbc = new RecordingJdbc(); var transactions = new RecordingTransactions();
        var store = new ManualL01RecoveryFixtureRepair.JdbcStore(jdbc, transactions, Map.of(), 1, UUID.randomUUID()) {
            public Map<String, Object> inspect() { var facts = ready(); facts.put("noPriorRepair", false); return facts; }
        };
        assertThatThrownBy(() -> store.commit("SYNTHETIC_ENCODED_VALUE")).hasMessage("REPAIR_GUARD_NO_PRIOR_REPAIR");
        assertThat(jdbc.sql).noneMatch(sql -> sql.stripLeading().startsWith("update") || sql.stripLeading().startsWith("insert"));
        assertThat(transactions.commits).isZero(); assertThat(transactions.rollbacks).isEqualTo(1);
    }
}
