package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/** One-off read-only diagnosis. No HTTP, Spring startup, acceptance or fixture changes. */
public final class ManualL01GateReadOnly {
    public static void main(String[] args) {
        try {
            UUID run = UUID.fromString(args[0]);
            var state = ManualL01HumanTrial.JSON.readTree(Files.readAllBytes(ManualL01HumanTrialServer.STATE));
            if (!run.toString().equals(state.path("runId").asText())
                    || !"ruta_fija_test".equals(state.path("database").asText())
                    || !run.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip()))
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
                var accounts = jdbc.queryForList("""
                    select u.id::text account_id, u.organization_id::text organization_id,
                        u.active, a.status, a.password_set_at, a.email_verified_at, a.completed_at,
                        a.credential_consumed_at, a.activation_token_revoked_at,
                        exists(select 1 from person_identity p where p.user_id=u.id) identity_reviewed
                    from app_user u join admin_activation a on a.admin_user_id=u.id
                    where u.id=? and u.organization_id=? and u.role='ADMIN'
                        and a.created_by_super_admin_id=? and u.email=?
                        and u.full_name='SYNTHETIC HUMAN ADMIN'
                    """, admin, org, owner, "admin.human." + run.toString().substring(0,8) + "@rutafija.test");
                if(accounts.size()!=1) throw new IllegalStateException("EXACT_SYNTHETIC_ACCOUNT_REQUIRED");
                var acceptances = jdbc.queryForList("""
                    select a.id::text acceptance_id, a.admin_user_id::text account_id,
                        a.organization_id::text organization_id, a.document_id::text document_id,
                        a.document_version, a.content_sha256, a.accepted_at,
                        (a.document_version=d.version and a.content_sha256=d.content_sha256
                            and a.content_sha256=? and d.document_key='ADMIN_TERMS'
                            and d.version='1.0.0' and not d.synthetic) approved_document_matches,
                        (act.password_set_at<=act.email_verified_at
                            and act.email_verified_at<=a.accepted_at
                            and a.accepted_at<=act.completed_at) ordering_valid,
                        exists(select 1 from audit_event ev where ev.user_id=u.id
                            and ev.organization_id=a.organization_id and ev.entity_id=d.id
                            and ev.action='ADMIN_ACTIVATION_TERMS_ACCEPTED') acceptance_audited
                    from admin_terms_acceptance a join app_user u on u.id=a.admin_user_id
                    join admin_activation act on act.admin_user_id=u.id
                    join admin_terms_document d on d.id=a.document_id
                    where a.admin_user_id=? and a.organization_id=?
                    """, ManualL01HumanTrial.APPROVED_SHA, admin, org);
                var result = new LinkedHashMap<String,Object>();
                result.put("inspectionAtUtc", Instant.now().toString());
                result.put("runId",run.toString());
                result.put("environment", ManualL01HumanTrial.snapshot(jdbc));
                result.put("exactSyntheticAdmin", accounts);
                result.put("acceptances", acceptances);
                result.put("observedFlags", Map.of(
                    "adminCreatedInactiveObserved",state.path("adminCreatedInactiveObserved").asBoolean(),
                    "explicitAcceptanceHttpObserved",state.path("explicitAcceptanceHttpObserved").asBoolean(),
                    "activatedAdminLoginObserved",state.path("activatedAdminLoginObserved").asBoolean(),
                    "preAcceptanceGateRecorded",state.has("preAcceptanceGate"),
                    "humanAdminConfirmationRecorded",state.path("humanAdminConfirmation").asBoolean()));
                result.put("reportedFailedCommand", "gate");
                result.put("gatePrecondition", acceptances.isEmpty() ? "REVIEW_REQUIRED" : "REFUSED_ACCEPTANCE_ALREADY_PRESENT");
                result.put("preAcceptanceGateResult", "NOT_OBSERVED_CANNOT_RECONSTRUCT_RETROACTIVELY");
                result.put("markerMatches", true);
                result.put("readOnly", true);
                result.put("databaseWrites",0);
                result.put("smtpAttempts",0);
                result.put("cleanupExecuted",false);
                result.put("manualSyntheticAdminResult","PENDING");
                c.rollback();
                System.out.println(ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            }
        } catch (Exception failure) {
            System.err.println("GATE_DIAGNOSIS=REFUSED_DETAILS_REDACTED");
            System.exit(1);
        }
    }
}
