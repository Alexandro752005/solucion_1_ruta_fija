package pe.rutafija.support;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Independent read-only verification after selective retirement of L06-CON-01. */
public final class L06ConnectedPostflight {
    private static final String RUN="51e69ee6-e4a0-4c3f-9a35-f19de0072422";
    private static void require(boolean value,String code){if(!value)throw new IllegalStateException(code);}
    private static String scalar(Connection c,String sql,Object... args)throws SQLException{
        try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){require(r.next(),"EXPECTED_READ_ROW");return r.getString(1);}}
    }
    private static UUID id(JsonNode s,String field){return UUID.fromString(s.path(field).asText());}
    public static void main(String[] args){try{
        require(args.length==0,"NO_ARGUMENTS_ALLOWED");var json=new ObjectMapper();Path root=Path.of("..").toAbsolutePath().normalize();
        var state=json.readTree(Files.readAllBytes(root.resolve("backend/.local/l06-connected-state.json")));
        require(RUN.equals(state.path("runId").asText()),"EXACT_RUN_REQUIRED");
        require("PASS_REAL_CRM_FLUTTER_HTTP_POSTGRES_SIMULATED_MAIL".equals(state.path("result").asText()),"CONNECTED_PASS_REQUIRED");
        require(state.path("TEST_CLEANUP_RELEASED").asBoolean()&&state.path("privateFixtureSecretsRemoved").asBoolean(),"SELECTIVE_CLOSURE_REQUIRED");
        for(String step:List.of("SECONDARY_MEMBERSHIP","FLUTTER_REPORT","CRM_RESPONSIBILITY_FOLLOWUP","FLUTTER_INFORMATION","CRITICAL_NOTICE_WITHOUT_AUTOMATIC_INTERRUPTION","CRM_STRUCTURED_CLOSURE","CRM_RELATED_NEW_CASE","CRM_IMPORTANT_PUBLICATION","FLUTTER_RECEIPT_NOT_ACK","LEAVE_GROUP","FLUTTER_RETAIN_OLD_READ_ACK","CRM_REVIEW_WITHDRAWAL","FLUTTER_WITHDRAWN_SCOPE","INCIDENT_VERSION_RACE","ACK_WITHDRAWAL_RACE","SAME_BINARY_RETURN"))require(state.path(step).asBoolean(),"CONNECTED_STAGE_NOT_PASSED");
        for(String name:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active","l05-connected.active","l06-connected.active","l06-connected.private"))require(!Files.exists(root.resolve("backend/.local/"+name)),"PROTECTION_OR_SECRET_STILL_PRESENT");
        var families=new TreeSet<UUID>();Path folder=root.resolve("docs/evidencia/L-06/conectado-"+RUN);
        try(var paths=Files.list(folder)){for(Path p:paths.filter(p->p.getFileName().toString().startsWith("observacion-")).toList()){
            var observed=json.readTree(Files.readAllBytes(p));if(observed.hasNonNull("browserRefreshFamilyId"))families.add(id(observed,"browserRefreshFamilyId"));
        }}
        var database=NativePostgresTestDatabase.load();database.assertTestRolePermissions();
        try(Connection c=ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())){
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);
            require("ruta_fija_test/rf_test".equals(scalar(c,"select current_database()||'/'||current_user")),"TEST_TARGET_REQUIRED");
            require("44".equals(scalar(c,"select version from flyway_schema_history where success order by installed_rank desc limit 1")),"V44_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from flyway_schema_history where not success")),"SUCCESSFUL_SCHEMA_REQUIRED");
            UUID account=id(state,"accountId"),driver=id(state,"driverId"),org=id(state,"organizationId"),incident=id(state,"currentIncidentId"),related=id(state,"relatedIncidentId"),announcement=id(state,"currentAnnouncementId");
            require("1".equals(scalar(c,"select count(*) from app_user u join driver d on d.user_id=u.id where u.id=? and d.id=? and u.organization_id=? and d.organization_id=u.organization_id and u.role='CONDUCTOR' and not u.active and not d.active",account,driver,org)),"EXACT_ACCOUNT_DRIVER_DISABLED_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from refresh_token where user_id=? and revoked_at is null",account)),"CONDUCTOR_SESSIONS_REVOKED_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from driver_unit_period where driver_id=? and status='AUTHORIZED' and revoked_at is null",driver)),"NO_AUTHORIZED_PERIOD_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from assignment where driver_id=? and status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')",driver)),"NO_LIVE_COMMITMENT_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from vehicle where id=? and organization_id=? and not active",id(state,"vehicleId"),org)),"EXACT_VEHICLE_RETIRED_REQUIRED");
            for(String field:List.of("groupId","secondaryGroupId"))require("1".equals(scalar(c,"select count(*) from transport_group where id=? and organization_id=? and not active",id(state,field),org)),"EXACT_GROUP_RETIRED_REQUIRED");
            for(UUID family:families)require("0".equals(scalar(c,"select count(*) from refresh_token where family_id=? and revoked_at is null",family)),"TEST_ADMIN_SESSION_REVOKED_REQUIRED");
            var ledger=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))ledger.put(role,Integer.valueOf(scalar(c,"select count(*) from pilot_account_creation where role=?",role)));
            require(ledger.equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",5)),"EXACT_HISTORICAL_LEDGER_REQUIRED");require("2".equals(scalar(c,"select count(*) from admin_terms_acceptance")),"ACCEPTANCES_PRESERVED_REQUIRED");
            require("2".equals(scalar(c,"select count(*) from incident where driver_id=? and organization_id=? and status='RESOLVED'",driver,org)),"TWO_TERMINAL_INCIDENTS_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from incident where id=? and previous_incident_id=? and relation_reason='RECURRENCE'",related,incident)),"RELATED_CASE_ORIGINAL_PRESERVED_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from incident_action where incident_id=? and kind='REPORT'",incident)),"ONE_ORIGINAL_REPORT_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from incident_action where incident_id=? and kind='INFORMATION'",incident)),"ONE_ADDITIONAL_INFORMATION_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from incident_action where incident_id=? and kind='ASSESSMENT'",related)),"ONE_CONCURRENT_REVIEW_WINNER_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from announcement_recipient r join announcement_receipt a on a.announcement_id=r.announcement_id and a.user_id=r.user_id where r.announcement_id=? and r.user_id=? and r.group_at_publication=? and r.withdrawn_at is not null and a.delivered_at is not null and a.read_at is not null and a.read_at>=a.delivered_at and r.withdrawn_at>=a.read_at",announcement,account,id(state,"secondaryGroupId"))),"EXPLICIT_READ_THEN_SECURITY_WITHDRAWAL_PRESERVED_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from announcement_recipient where announcement_id=? and user_id=?",id(state,"newGroupAnnouncementId"),account)),"NO_NEW_GROUP_RECIPIENT_AFTER_EXIT_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from announcement_recipient where announcement_id=? and user_id=? and withdrawn_at is not null",id(state,"raceAnnouncementId"),account)),"CONCURRENT_SECURITY_WITHDRAWAL_PRESERVED_REQUIRED");
            int nonDelivery=Integer.parseInt(scalar(c,"select count(*) from operation_notice where organization_id=? and references_json->>'incidentId'=? and external_status='NOT_CONFIGURED'",org,incident.toString()));require(nonDelivery>0,"DURABLE_EXTERNAL_NON_DELIVERY_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from operation_notice where organization_id=? and (references_json->>'incidentId' in (?,?) or references_json->>'announcementId' in (?,?,?)) and external_status='PROVIDER_ACCEPTED'",org,incident.toString(),related.toString(),announcement.toString(),id(state,"newGroupAnnouncementId").toString(),id(state,"raceAnnouncementId").toString())),"NO_FALSE_PROVIDER_DELIVERY_REQUIRED");
            for(String table:List.of("pilot_account_creation","admin_terms_acceptance","scheduled_service","assignment","incident","announcement")){
                var original=state.path("antecedentRowHashes").path(table);require(original.isObject(),"BASELINE_REQUIRED");var ids=original.fieldNames();
                while(ids.hasNext()){String originalId=ids.next();String key=table.equals("pilot_account_creation")?"user_id":"id";String row=scalar(c,"select to_jsonb(t)::text from "+table+" t where "+key+"=?",UUID.fromString(originalId));String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(row.getBytes(StandardCharsets.UTF_8)));require(hash.equals(original.path(originalId).asText()),"ANTECEDENT_ROW_CHANGED");}
            }
            require("true".equals(scalar(c,"select pg_try_advisory_lock(7518440514)::text")),"CLEANUP_LOCK_MUST_BE_FREE");require("true".equals(scalar(c,"select pg_advisory_unlock(7518440514)::text")),"CLEANUP_LOCK_RELEASE_REQUIRED");c.rollback();
            var result=new LinkedHashMap<String,Object>();result.put("observedAt",Instant.now().toString());result.put("runId",RUN);result.put("database","ruta_fija_test");result.put("role","rf_test");result.put("readOnly",true);result.put("schemaVersion","44");result.put("result","PASS_EXACT_CONNECTED_RETIREMENT");result.put("newAccount",account);result.put("ledger",ledger);result.put("remainingHistoricalCreations",Map.of("SUPER_ADMIN",0,"ADMIN",1,"CONDUCTOR",15));result.put("termsAcceptances",2);result.put("accountsAndResourcesDisabled",true);result.put("periodsRevokedNoLiveCommitments",true);result.put("allConductorRefreshRevoked",true);result.put("adminTestRefreshFamiliesRevoked",families);result.put("incidentsAndReadWithdrawalHistoryPreserved",true);result.put("durableNotConfiguredNotices",nonDelivery);result.put("antecedentRowsPreserved",true);result.put("privateFixtureSecretsRemoved",true);result.put("TEST_CLEANUP_RELEASED",true);result.put("cleanupLockFree",true);result.put("globalCleanup",false);result.put("pilotEnabled",false);result.put("L07Started",false);
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }catch(Exception failure){String code=Objects.toString(failure.getMessage(),"");if(!code.matches("[A-Z][A-Z0-9_]{0,100}"))code="DETAILS_REDACTED";String sqlState=failure instanceof SQLException sql?Objects.toString(sql.getSQLState(),"NONE"):"NONE";System.out.println("{\"result\":\"REFUSED\",\"check\":\""+code+"\",\"causeType\":\""+failure.getClass().getSimpleName()+"\",\"sqlState\":\""+sqlState+"\"}");System.exit(1);}}
}
