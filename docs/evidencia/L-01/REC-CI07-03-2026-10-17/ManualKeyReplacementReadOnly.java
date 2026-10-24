package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.Files;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** SELECT plus EXPLAIN without ANALYZE only; no mutation, lock acquisition, key generation, or private-file access. */
public final class ManualKeyReplacementReadOnly {
    public static void main(String[] args) {
        String phase = "START";
        try {
            var state = ManualL01HumanTrial.JSON.readValue(Files.readAllBytes(ManualL01HumanTrialServer.STATE), Map.class);
            if (!ManualL01RecoveryFixtureRepair.RUN.toString().equals(state.get("runId"))
                    || !ManualL01RecoveryFixtureRepair.RUN.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip()))
                throw new IllegalStateException("EXACT_CASE_REQUIRED");
            UUID segment = UUID.fromString(state.get("recoveryObservationSegmentId").toString());
            var result = new LinkedHashMap<String,Object>();
            try (Connection connection = ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())) {
                connection.setReadOnly(true); connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection,true)); jdbc.setQueryTimeout(5);
                ManualL01HumanTrial.assertDatabase(jdbc);
                var before = ManualL01HumanTrial.snapshot(jdbc);
                phase = "SERVING_LOCK_REFERENCE";
                var pids = jdbc.queryForList("""
                        select pid from pg_locks where locktype='advisory' and granted and objsubid=1
                          and database=(select oid from pg_database where datname=current_database())
                          and classid::bigint=(?::bigint >> 32) and objid::bigint=(?::bigint & 4294967295)
                        """, ManualL01HumanTrial.CLEANUP_LOCK,ManualL01HumanTrial.CLEANUP_LOCK);
                if (pids.size()!=1) throw new IllegalStateException("EXACT_SERVING_LOCK_REQUIRED");
                int servingPid = ((Number)pids.getFirst().get("pid")).intValue();
                phase = "GUARDS_READ_ONLY";
                var store = new ManualL01RecoveryFixtureKeyReplacement.JdbcStore(jdbc,null,state,servingPid,segment);
                var facts = store.inspect();
                ManualL01RecoveryFixtureKeyReplacement.requireReady(facts);
                String oldPublic = jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1",String.class);
                phase = "UPDATE_PLAN_ONLY";
                try (PreparedStatement statement = connection.prepareStatement("EXPLAIN " + ManualL01RecoveryFixtureKeyReplacement.UPDATE_SQL)) {
                    statement.setString(1,oldPublic); statement.setObject(2,ManualL01RecoveryFixtureRepair.OWNER);
                    statement.setObject(3,ManualL01RecoveryFixtureRepair.INSTALLATION); statement.setString(4,oldPublic);
                    try (ResultSet plan=statement.executeQuery()) { if(!plan.next())throw new IllegalStateException("UPDATE_PLAN_ABSENT"); }
                }
                phase = "AUDIT_PLAN_ONLY";
                try (PreparedStatement statement = connection.prepareStatement("EXPLAIN " + ManualL01RecoveryFixtureKeyReplacement.AUDIT_SQL)) {
                    statement.setObject(1,UUID.randomUUID()); statement.setObject(2,ManualL01RecoveryFixtureRepair.OWNER);
                    statement.setString(3,ManualL01RecoveryFixtureKeyReplacement.ACTION); statement.setObject(4,ManualL01RecoveryFixtureRepair.INSTALLATION);
                    statement.setString(5,"{\"diagnosticOnly\":true}");
                    try (ResultSet plan=statement.executeQuery()) { if(!plan.next())throw new IllegalStateException("AUDIT_PLAN_ABSENT"); }
                }
                var after = ManualL01HumanTrial.snapshot(jdbc);
                if(!before.equals(after)) throw new IllegalStateException("READ_ONLY_SNAPSHOT_CHANGED");
                result.put("inspectionAtUtc",Instant.now().toString()); result.put("runId",ManualL01RecoveryFixtureRepair.RUN.toString());
                result.put("recoveryObservationSegmentId",segment.toString()); result.put("environment",after);
                result.put("guardFacts",facts); result.put("requiredGuardsPassed",ManualL01RecoveryFixtureKeyReplacement.REQUIRED.size());
                result.put("servingLockBackendPid",servingPid); result.put("servingLockReferenceBorrowedOnlyForReadOnlyInspection",true);
                result.put("inspectorAcquiredAnyLock",false); result.put("actualServingCommandWillRequireOwnLock",true);
                result.put("updateAndAuditExplainWithoutAnalyzePassed",true); result.put("applicationDmlExecuted",false);
                result.put("schemaDdlExecuted",false); result.put("commitCalled",false); result.put("newKeyGenerated",false);
                result.put("privateKeyFileContentsRead",false); result.put("emergencyCodeOrHashSelected",false);
                result.put("httpOrSmtpRequests",0); result.put("fixtureKeyReplacementExecuted",false);
                result.put("TEST_CLEANUP_RELEASED",false); connection.rollback();
            }
            System.out.println(ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (Exception failure) {
            System.err.println("KEY_PREP_NATIVE_READ_ONLY=REFUSED_DETAILS_REDACTED");
            System.err.println("KEY_PREP_NATIVE_PHASE="+phase);
            if(failure.getMessage()!=null && failure.getMessage().matches("[A-Z][A-Z0-9_]{0,100}")) System.err.println("SAFE_REASON="+failure.getMessage());
            Throwable cause=failure; while(cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof SQLException sql) System.err.println("SQL_STATE="+sql.getSQLState());
            System.exit(1);
        }
    }
}
