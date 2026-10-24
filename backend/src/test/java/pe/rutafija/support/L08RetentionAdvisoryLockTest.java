package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import pe.rutafija.backup.DatabaseIntegrity;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL, SELECTs and reverted transactions only. No plan, account,
 * retirement or tombstone is created. This tests the Java advisory-lock boundary,
 * not the full SQL retirement race or its commit/audit behaviour. */
class L08RetentionAdvisoryLockTest {
    private static final String KEY = "hashtextextended('ruta_fija.retention.execute:' || ?::text,0) | '-9223372036854775808'::bigint";
    private static final String LOCK = "select pg_advisory_xact_lock(" + KEY + ")";

    @Test void samePlanWaitsForTheOtherTransactionAndRollbackReleasesIt() throws Exception {
        try (var scope = new ReadOnlyScope(); var a = scope.runtime(); var b = scope.runtime()) {
            UUID plan = UUID.randomUUID();
            int aPid = pid(a), bPid = pid(b);
            lock(a, plan);
            var executor = Executors.newSingleThreadExecutor();
            Future<Boolean> waiting = null;
            try {
                waiting = executor.submit(() -> { lock(b, plan); return true; });
                boolean blockedByA = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline && !waiting.isDone()) {
                    try (var query = scope.observer.prepareStatement("select ? = any(pg_blocking_pids(?))")) {
                        query.setInt(1, aPid); query.setInt(2, bPid);
                        try (var row = query.executeQuery()) { row.next(); blockedByA = row.getBoolean(1); }
                    }
                    if (blockedByA) break;
                    Thread.sleep(25);
                }
                assertThat(blockedByA).as("second real connection waits on first backend").isTrue();
                assertThat(waiting.isDone()).isFalse();
                a.rollback();
                assertThat(waiting.get(5, TimeUnit.SECONDS)).isTrue();
                b.rollback();
                lock(a, plan);
                a.rollback();
                scope.assertPreserved();
            } finally {
                a.rollback();
                if (waiting != null && !waiting.isDone()) b.abort(Runnable::run);
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test void distinctPlansProceedAndNegativeNamespaceDoesNotUseCleanupGuard() throws Exception {
        try (var scope = new ReadOnlyScope(); var a = scope.runtime(); var b = scope.runtime()) {
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            long firstKey = key(a, first), secondKey = key(b, second);
            assertThat(firstKey).isNegative(); assertThat(secondKey).isNegative();
            assertThat(firstKey).isNotEqualTo(secondKey).isNotEqualTo(7_518_440_514L);
            lock(a, first);
            lock(b, second); // statement_timeout bounds an accidental shared lock
            a.rollback(); b.rollback();
            scope.assertPreserved();
        }
    }

    private static void lock(Connection c, UUID id) throws SQLException {
        try (var q = c.prepareStatement(LOCK)) { q.setObject(1, id); q.setQueryTimeout(8); q.execute(); }
    }
    private static long key(Connection c, UUID id) throws SQLException {
        try (var q = c.prepareStatement("select " + KEY)) {
            q.setObject(1, id); try (var r = q.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    private static int pid(Connection c) throws SQLException {
        try (var q = c.createStatement(); var r = q.executeQuery("select pg_backend_pid()")) { r.next(); return r.getInt(1); }
    }
    private static final class ReadOnlyScope implements AutoCloseable {
        final Map<String, Object> properties = new HashMap<>();
        final Connection observer;
        final Map<String, Object> before;
        ReadOnlyScope() throws Exception {
            NativePostgresTestDatabase.assertNoL08ConnectedFixture(Path.of(".local/l08-connected.active"));
            NativePostgresTestDatabase.assertNoL08ConnectedFixture(Path.of("backend/.local/l08-connected.active"));
            var database = NativePostgresTestDatabase.load();
            database.assertTestRolePermissions();
            database.registerSpringProperties((k, v) -> properties.put(k, v.get()));
            observer = DriverManager.getConnection(value("spring.flyway.url"), value("spring.flyway.user"), value("spring.flyway.password"));
            try {
                observer.setReadOnly(true); observer.setAutoCommit(false);
                try (var s = observer.createStatement(); var r = s.executeQuery("select pg_try_advisory_xact_lock(7518440514)")) {
                    r.next(); assertThat(r.getBoolean(1)).as("fixture guard must be free").isTrue();
                }
                assertTarget(observer, "rf_migrator");
                before = DatabaseIntegrity.capture(observer);
            } catch (Exception | Error failure) { observer.close(); throw failure; }
        }
        String value(String key) { return Objects.requireNonNull(properties.get(key)).toString(); }
        Connection runtime() throws Exception {
            var c = DriverManager.getConnection(value("spring.datasource.url"), value("spring.datasource.username"), value("spring.datasource.password"));
            try {
                c.setReadOnly(true); c.setAutoCommit(false);
                try (var q = c.createStatement()) { q.execute("set local statement_timeout='8s'"); }
                assertTarget(c, "rf_test"); return c;
            } catch (Exception | Error failure) { c.close(); throw failure; }
        }
        static void assertTarget(Connection c, String role) throws SQLException {
            try (var s = c.createStatement(); var r = s.executeQuery("select current_database(),current_user,inet_server_port(),current_setting('transaction_read_only'),(select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1)")) {
                r.next(); assertThat(r.getString(1)).isEqualTo("ruta_fija_test"); assertThat(r.getString(2)).isEqualTo(role);
                assertThat(r.getInt(3)).isEqualTo(5432); assertThat(r.getString(4)).isEqualTo("on"); assertThat(r.getString(5)).isEqualTo("50");
            }
        }
        void assertPreserved() throws SQLException { assertThat(DatabaseIntegrity.capture(observer)).isEqualTo(before); }
        public void close() throws SQLException {
            try { observer.rollback(); } finally { observer.close(); properties.clear(); }
        }
    }
}
