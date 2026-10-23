package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Exact-case locks and EXPLAIN without ANALYZE. Never calls repair/execute/commit or reads private keys. */
public final class ManualRecoveryRepairPreflight {
    public static void main(String[] args) {
        String phase = "START";
        Map<String, Object> safe = new LinkedHashMap<>();
        try {
            var state = ManualL01HumanTrial.JSON.readValue(Files.readAllBytes(ManualL01HumanTrialServer.STATE), Map.class);
            if (!ManualL01RecoveryFixtureRepair.RUN.toString().equals(state.get("runId"))
                    || !ManualL01RecoveryFixtureRepair.RUN.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip()))
                throw new IllegalStateException("CASE_MISMATCH");
            var result = new LinkedHashMap<String, Object>();
            try (Connection connection = ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())) {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                ManualL01HumanTrial.assertDatabase(jdbc);
                if (!Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, ManualL01HumanTrial.CLEANUP_LOCK)))
                    throw new IllegalStateException("CASE_ALREADY_RUNNING_OR_BUSY");
                try {
                    connection.setAutoCommit(false);
                    jdbc.execute("set local lock_timeout='2s'");
                    jdbc.execute("set local statement_timeout='5s'");
                    var before = ManualL01HumanTrial.snapshot(jdbc);
                    phase = "COUNTER_ADVISORY_LOCKS";
                    ManualL01RecoveryFixtureRepair.lockHistoricalCreationCounters(jdbc);
                    jdbc.queryForList("select id from app_user where id=? for update", ManualL01RecoveryFixtureRepair.OWNER);
                    jdbc.queryForList("select id from super_admin_recovery_credential where id=1 for update");
                    jdbc.queryForList("select id from app_user where id=? for share", ManualL01RecoveryFixtureRepair.ADMIN);
                    jdbc.queryForList("select id from organization where id=? for share", ManualL01RecoveryFixtureRepair.ORGANIZATION);
                    int pid = Objects.requireNonNull(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    var store = new ManualL01RecoveryFixtureRepair.JdbcStore(jdbc, null, state, pid, null);
                    phase = "GUARDS";
                    var facts = store.inspect();
                    safe.put("guardFacts", facts);
                    ManualL01RecoveryFixtureRepair.requireReady(facts);
                    phase = "UPDATE_EXPLAIN";
                    try (PreparedStatement explain = connection.prepareStatement("EXPLAIN " + ManualL01RecoveryFixtureRepair.UPDATE_SQL)) {
                        explain.setString(1, "SYNTHETIC_PREFLIGHT_NOT_AN_ACTIVE_HASH");
                        explain.setObject(2, ManualL01RecoveryFixtureRepair.OWNER);
                        explain.setObject(3, ManualL01RecoveryFixtureRepair.INSTALLATION);
                        try (ResultSet plan = explain.executeQuery()) { if (!plan.next()) throw new IllegalStateException("UPDATE_PLAN_ABSENT"); }
                    }
                    phase = "AUDIT_EXPLAIN";
                    try (PreparedStatement explain = connection.prepareStatement("EXPLAIN " + ManualL01RecoveryFixtureRepair.AUDIT_SQL)) {
                        explain.setObject(1, UUID.randomUUID()); explain.setObject(2, ManualL01RecoveryFixtureRepair.OWNER);
                        explain.setString(3, ManualL01RecoveryFixtureRepair.AUDIT_ACTION);
                        explain.setObject(4, ManualL01RecoveryFixtureRepair.INSTALLATION);
                        explain.setString(5, "{\"fixtureDiagnosticOnly\":true}");
                        try (ResultSet plan = explain.executeQuery()) { if (!plan.next()) throw new IllegalStateException("AUDIT_PLAN_ABSENT"); }
                    }
                    phase = "COMPARE_SELECT_SNAPSHOTS";
                    var after = ManualL01HumanTrial.snapshot(jdbc);
                    if (!before.equals(after)) throw new IllegalStateException("PREFLIGHT_STATE_CHANGED");
                    result.put("runId", ManualL01RecoveryFixtureRepair.RUN.toString());
                    result.put("inspectionAtUtc", Instant.now().toString());
                    result.put("guardFacts", facts);
                    result.put("environmentDuringPreflight", after);
                    result.put("rowAndTableLocksTested", true);
                    result.put("updateAndAuditExplainWithoutAnalyzePassed", true);
                    result.put("applicationDmlExecuted", false);
                    result.put("schemaDdlExecuted", false);
                    result.put("commitCalled", false);
                    result.put("emergencyCodeGeneratedOrPrinted", false);
                    result.put("actualCasePrivateKeysRead", false);
                    result.put("httpOrSmtpRequests", 0);
                } finally {
                    connection.rollback(); connection.setAutoCommit(true);
                    jdbc.queryForObject("select pg_advisory_unlock(?)", Boolean.class, ManualL01HumanTrial.CLEANUP_LOCK);
                }
            }
            result.put("afterPreflight", ManualL01HumanTrial.inspect());
            result.put("originalMarkerPreserved", Files.exists(ManualL01HumanTrialServer.ACTIVE));
            result.put("TEST_CLEANUP_RELEASED", false);
            System.out.println(ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (Exception failure) {
            safe.put("phase", phase);
            if (failure.getMessage() != null && failure.getMessage().matches("[A-Z][A-Z0-9_]{0,100}")) safe.put("safeReason", failure.getMessage());
            Throwable cause = failure;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof SQLException sql) safe.put("sqlState", sql.getSQLState());
            try { System.out.println(ManualL01HumanTrial.JSON.writeValueAsString(safe)); } catch (Exception ignored) { }
            System.err.println("REPAIR_NATIVE_PREFLIGHT=REFUSED_DETAILS_REDACTED"); System.exit(1);
        }
    }
}
