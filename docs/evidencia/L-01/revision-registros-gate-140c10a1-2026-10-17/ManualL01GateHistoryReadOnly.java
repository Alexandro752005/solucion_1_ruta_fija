package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/** Historical evidence inspection only: SELECT, no HTTP, no fixture/acceptance changes. */
public final class ManualL01GateHistoryReadOnly {
    public static void main(String[] args) {
        try {
            UUID run = UUID.fromString(args[0]);
            var state = ManualL01HumanTrial.JSON.readTree(Files.readAllBytes(ManualL01HumanTrialServer.STATE));
            if (!run.toString().equals(state.path("runId").asText())
                    || !run.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip())
                    || !"ruta_fija_test".equals(state.path("database").asText()))
                throw new IllegalStateException("CASE_MISMATCH");
            UUID owner = UUID.fromString(state.path("ownerId").asText());
            UUID org = UUID.fromString(state.path("organizationId").asText());
            UUID admin = UUID.fromString(state.path("adminId").asText());
            try (Connection c = ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())) {
                c.setReadOnly(true);
                c.setAutoCommit(false);
                c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(c, true));
                ManualL01HumanTrial.assertDatabase(jdbc);
                if(ManualL01HumanTrial.count(jdbc, """
                    select count(*) from app_user u join admin_activation a on a.admin_user_id=u.id
                    where u.id=? and u.organization_id=? and u.role='ADMIN'
                        and a.created_by_super_admin_id=? and u.email=?
                        and u.full_name='SYNTHETIC HUMAN ADMIN'
                    """, admin, org, owner, "admin.human."+run.toString().substring(0,8)+"@rutafija.test") != 1)
                    throw new IllegalStateException("EXACT_CASE_REQUIRED");
                var acceptances = jdbc.queryForList("""
                    select a.id::text acceptance_id,a.document_id::text document_id,
                        a.document_version,a.content_sha256,a.accepted_at,
                        act.id::text activation_id,act.created_at,act.password_set_at,
                        act.email_verified_at,act.completed_at,act.status,u.active
                    from admin_terms_acceptance a join app_user u on u.id=a.admin_user_id
                    join admin_activation act on act.admin_user_id=u.id
                    where a.admin_user_id=? and a.organization_id=?
                    """,admin,org);
                if(acceptances.size()!=1) throw new IllegalStateException("EXACT_ACCEPTANCE_REQUIRED");
                UUID activation=UUID.fromString(acceptances.getFirst().get("activation_id").toString());
                var audit = jdbc.queryForList("""
                    select id::text,user_id::text,organization_id::text,action,
                        entity_type,entity_id::text,occurred_at,
                        occurred_at < (select accepted_at from admin_terms_acceptance
                            where id=?) before_acceptance
                    from audit_event
                    where (user_id=? or user_id=? or organization_id=? or entity_id=? or entity_id=?)
                    order by occurred_at,id
                    """,UUID.fromString(acceptances.getFirst().get("acceptance_id").toString()),
                    owner,admin,org,admin,activation);
                var activationEvents = jdbc.queryForList("""
                    select id::text,action,actor_id::text,occurred_at,
                        occurred_at < (select accepted_at from admin_terms_acceptance
                            where id=?) before_acceptance
                    from admin_activation_event where activation_id=? order by occurred_at,id
                    """,UUID.fromString(acceptances.getFirst().get("acceptance_id").toString()),activation);
                var result = new LinkedHashMap<String,Object>();
                result.put("inspectionAtUtc",Instant.now().toString());
                result.put("runId",run.toString());
                result.put("environment",ManualL01HumanTrial.snapshot(jdbc));
                result.put("acceptanceAndActivation",acceptances);
                result.put("caseAuditEvents",audit);
                result.put("caseActivationEvents",activationEvents);
                result.put("possibleEvidenceTableNames",jdbc.queryForList("""
                    select table_name from information_schema.tables where table_schema='public'
                        and table_name ~ '(http|access|request|attempt|log|audit|event)'
                    order by table_name
                    """,String.class));
                result.put("existingManualAdminResult",state.path("manualSyntheticAdminResult").asText());
                result.put("preAcceptanceGateRecorded",state.has("preAcceptanceGate"));
                result.put("activatedAdminLoginObserved",state.path("activatedAdminLoginObserved").asBoolean());
                result.put("readOnly",true);
                result.put("databaseWrites",0);
                result.put("httpRequests",0);
                result.put("smtpAttempts",0);
                result.put("additionalAccountsCreated",0);
                result.put("acceptanceReset",false);
                c.rollback();
                System.out.println(ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            }
        } catch(Exception failure) {
            System.err.println("GATE_HISTORY_INSPECTION=REFUSED_DETAILS_REDACTED");
            System.exit(1);
        }
    }
}
