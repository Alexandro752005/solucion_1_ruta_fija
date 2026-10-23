package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** L06 test-only read-only validation or explicit additive migration. Never cleans or repairs. */
public final class L06DatabasePreflight {
    private static final long LOCK_KEY = 7_518_440_514L;
    private static final String RUN = "140c10a1-be0f-4d08-a6d4-c796e2ed107a";
    private static final String OWNER = "088518c1-821c-4316-9f87-090f51ac44e0";
    private static final String MANUAL_ADMIN = "92ebc07c-1319-4850-a734-f792cb299979";
    private static final String ORGANIZATION = "1a759298-7831-4aaf-9299-c3920a9cc28a";
    private static final String MANUAL_ACCEPTANCE = "c496b63c-735f-48cc-be56-90f7ec1464e1";
    private static final String PUBLISHED_HASH = "20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static String phase = "INITIALIZATION";
    private static Map<String,Object> validationDiagnostics=Map.of();

    private L06DatabasePreflight() { }

    public static void main(String[] args) {
        try {
            silenceLogging();
            if (args.length > 1 || (args.length == 1 && !Set.of("--migrate-test-v43","--migrate-test-v44","--validate-test").contains(args[0])))
                throw new Refused("UNSUPPORTED_ARGUMENTS");
            boolean migrate = args.length == 1 && args[0].startsWith("--migrate-test");
            String target = args.length==1&&"--migrate-test-v43".equals(args[0])?"43":"44";
            boolean validate = args.length == 1 && "--validate-test".equals(args[0]);
            phase="PRIVATE_TEST_CONFIGURATION";
            NativePostgresTestDatabase database = NativePostgresTestDatabase.load();
            phase="TEST_ROLE_PERMISSIONS";
            database.assertTestRolePermissions();
            phase="HUMAN_MARKER";
            assertNoHumanMarker();
            phase="TEST_CONNECTION";
            try (Connection connection = connect(database, false)) {
                connection.setAutoCommit(false);
                connection.setReadOnly(true);
                if (!scalar(connection, "select current_database()").equals("ruta_fija_test")
                        || !scalar(connection, "select current_user").equals("rf_test"))
                    throw new Refused("TEST_TARGET_MISMATCH");
                phase="PROTECTION_LOCK";
                boolean locked = Boolean.parseBoolean(scalar(connection, "select pg_try_advisory_lock(" + LOCK_KEY + ")"));
                if (!locked) throw new Refused("CLEANUP_PROTECTION_LOCK_HELD");
                try {
                    assertNoHumanMarker();
                    Map<String,Object> before = snapshot(connection);
                    Map<String,Object> result = new LinkedHashMap<>();
                    result.put("observedAt", Instant.now().toString());
                    result.put("mode", migrate ? "EXPLICIT_MIGRATE_TEST_ONLY" : validate?"READ_ONLY_FLYWAY_VALIDATION":"READ_ONLY");
                    result.put("database", "ruta_fija_test"); result.put("role", "rf_test");
                    result.put("preservedHumanCaseRunId", RUN); result.put("before", before);result.put("targetVersion",target);
                    if(migrate&&Integer.parseInt(before.get("schemaVersion").toString())>Integer.parseInt(target))throw new Refused("TARGET_IS_HISTORICAL");
                    if(validate) {
                        phase="FLYWAY_READ_ONLY_VALIDATION";
                        var validation=Flyway.configure().dataSource(value(database,"testUrl"),value(database,"testUsername"),value(database,"testPassword"))
                                .locations("classpath:db/migration","classpath:db/test-migration").target(target).cleanDisabled(true).load().validateWithResult();
                        validationDiagnostics=validationSummary(validation);
                        result.put("flywayValidation",validationDiagnostics);
                        if(!validation.validationSuccessful)throw new Refused("FLYWAY_VALIDATION_FAILED");
                    }
                    if (migrate) {
                        // Configuration validation checks the same loopback/test target and migrator role.
                        phase="MIGRATOR_TARGET";
                        try (Connection migrator = connect(database, true)) {
                            if (!"ruta_fija_test".equals(scalar(migrator, "select current_database()"))
                                    || !"rf_migrator".equals(scalar(migrator, "select current_user")))
                                throw new Refused("MIGRATOR_TARGET_MISMATCH");
                        }
                        phase="FLYWAY_VALIDATE";
                        Flyway flyway = Flyway.configure()
                                .dataSource(value(database,"migratorUrl"), value(database,"migratorUsername"), value(database,"migratorPassword"))
                                .locations("classpath:db/migration", "classpath:db/test-migration")
                                .target(target).cleanDisabled(true).load();
                        var validation=flyway.validateWithResult();
                        validationDiagnostics=validationSummary(validation);
                        result.put("flywayValidationBeforeMigrate",validationDiagnostics);
                        boolean expectedPendingTarget=!validation.validationSuccessful&&validation.invalidMigrations.size()==1
                                &&target.equals(validation.invalidMigrations.getFirst().version)
                                &&validation.invalidMigrations.getFirst().errorDetails!=null
                                &&"RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(validation.invalidMigrations.getFirst().errorDetails.errorCode.toString());
                        if(!validation.validationSuccessful&&!expectedPendingTarget)throw new Refused("FLYWAY_VALIDATION_FAILED");
                        result.put("onlyExpectedPendingV"+target,expectedPendingTarget);
                        // migrate performs its own normal applied-history validation,
                        // and applies the explicitly authorized pending migration.
                        // No repair, checksum override or ignore pattern is configured.
                        // Release snapshot relation locks before DDL on those same tables.
                        // The session-level cleanup advisory lock remains held across rollback.
                        connection.rollback();
                        phase="FLYWAY_MIGRATE";
                        var migration = flyway.migrate();
                        result.put("migrationsExecuted", migration.migrationsExecuted);
                        connection.rollback(); // Refresh the read-only observation after the explicit migration.
                        Map<String,Object> after = snapshot(connection);
                        if (!target.equals(after.get("schemaVersion"))) throw new Refused("TARGET_NOT_APPLIED");
                        for (String key : List.of("ledger", "acceptanceCount", "ledgerSha256", "acceptancesSha256", "humanFixturesSha256", "publishedTermsSha256", "L02ClosedCaseSha256", "scheduledServicesSha256", "offersSha256", "incidentsOriginalsSha256", "announcementsSha256", "announcementReceiptsSha256"))
                            if (!Objects.equals(before.get(key), after.get(key))) throw new Refused("PRESERVATION_MISMATCH_" + key);
                        result.put("after", after); result.put("preservation", "PASS");
                    }
                    result.put("globalCleanupExecuted", false);
                    result.put("developmentOrPilotAccessed", false);
                    result.put("result", "PASS");
                    phase="SANITIZED_OUTPUT";
                    System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
                } finally {
                    String priorPhase=phase;
                    connection.rollback();
                    scalar(connection, "select pg_advisory_unlock(" + LOCK_KEY + ")");
                    connection.rollback();
                    phase=priorPhase;
                }
            }
        } catch (Exception failure) {
            String reason = failure instanceof Refused refused ? refused.reason : "OPERATION_FAILED_DETAILS_REDACTED";
            Throwable root=failure;for(int depth=0;depth<10&&root.getCause()!=null;depth++)root=root.getCause();
            String sqlState=root instanceof SQLException sql?Objects.toString(sql.getSQLState(),"NONE"):"NONE";
            if(!sqlState.matches("[A-Z0-9]{5}"))sqlState="NONE";
            Map<String,Object> rejected=new LinkedHashMap<>();rejected.put("result","REFUSED");rejected.put("phase",phase);
            rejected.put("reason",reason);rejected.put("causeType",root.getClass().getSimpleName());rejected.put("sqlState",sqlState);
            if(!validationDiagnostics.isEmpty())rejected.put("flywayValidation",validationDiagnostics);
            try { System.out.println(JSON.writeValueAsString(rejected)); }
            catch(Exception ignored){System.out.println("{\"result\":\"REFUSED\",\"reason\":\"SANITIZED_OUTPUT_FAILURE\"}");}
            System.exit(1);
        }
    }

    private static Map<String,Object> validationSummary(org.flywaydb.core.api.output.ValidateResult validation) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("successful",validation.validationSuccessful);
        result.put("validatedCount",validation.validateCount);
        result.put("code",validation.errorDetails==null?"NONE":validation.errorDetails.errorCode.toString());
        result.put("invalidMigrations",validation.invalidMigrations.stream().map(item->{
            Map<String,Object> migration=new LinkedHashMap<>();migration.put("version",Objects.toString(item.version,""));
            migration.put("description",Objects.toString(item.description,""));
            migration.put("code",item.errorDetails==null?"NONE":item.errorDetails.errorCode.toString());return migration;
        }).toList());
        return result;
    }

    private static Map<String,Object> snapshot(Connection connection) throws Exception {
        phase="SNAPSHOT_SCHEMA";
        String version = scalar(connection,"select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1");
        if (!Set.of("35","36","37","38","39","40","41","42","43","44").contains(version) || !"0".equals(scalar(connection,"select count(*) from flyway_schema_history where not success")))
            throw new Refused("UNEXPECTED_SCHEMA_HISTORY");
        LinkedHashMap<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", version);
        if(Integer.parseInt(version)>=35){
            snapshot.put("scheduledServiceCount",Integer.parseInt(scalar(connection,"select count(*) from scheduled_service")));
            snapshot.put("serviceOperationCount",Integer.parseInt(scalar(connection,"select count(*) from scheduled_service_operation")));
            snapshot.put("serviceOfferCount",Integer.parseInt(scalar(connection,"select count(*) from assignment where service_id is not null")));
        }
        snapshot.put("scheduledServicesSha256",digestRows(connection,"select id,organization_id,status,current_offer_id,version,updated_at from scheduled_service order by id"));
        snapshot.put("offersSha256",digestRows(connection,"select id,service_id,status,version,accepted_at,started_at,completed_at from assignment order by id"));
        if(Integer.parseInt(version)>=36)snapshot.put("executionSegments",Integer.parseInt(scalar(connection,"select count(*) from service_execution_segment")));
        if(Integer.parseInt(version)>=37) {
            snapshot.put("gpsPoints",Integer.parseInt(scalar(connection,"select count(*) from service_gps_point")));
            snapshot.put("gpsAccessRequests",Integer.parseInt(scalar(connection,"select count(*) from gps_access_request")));
        }
        if(Integer.parseInt(version)>=39) {
            snapshot.put("timeCorrections",Integer.parseInt(scalar(connection,"select count(*) from service_time_correction")));
            snapshot.put("timeCorrectionRequests",Integer.parseInt(scalar(connection,"select count(*) from service_time_request")));
        }
        if(Integer.parseInt(version)>=40) {
            snapshot.put("serviceAttentions",Integer.parseInt(scalar(connection,"select count(*) from service_attention")));
            snapshot.put("serviceAttentionResolutions",Integer.parseInt(scalar(connection,"select count(*) from service_attention_release")));
        }
        if(Integer.parseInt(version)>=41) {
            snapshot.put("serviceHandovers",Integer.parseInt(scalar(connection,"select count(*) from service_handover")));
            snapshot.put("serviceHandoverResolutions",Integer.parseInt(scalar(connection,"select count(*) from service_handover_resolution")));
        }
        phase="SNAPSHOT_LEDGER";
        Map<String,Integer> ledger = new LinkedHashMap<>();
        for (String role : List.of("SUPER_ADMIN","ADMIN","CONDUCTOR")) {
            int count = Integer.parseInt(scalar(connection,"select count(*) from pilot_account_creation where role='" + role + "'"));
            int limit = role.equals("SUPER_ADMIN") ? 1 : role.equals("ADMIN") ? 4 : 20;
            if (count > limit) throw new Refused("HISTORICAL_QUOTA_EXCEEDED");
            ledger.put(role,count);
        }
        snapshot.put("ledger",ledger);
        phase="SNAPSHOT_ACCEPTANCES";
        snapshot.put("acceptanceCount",Integer.parseInt(scalar(connection,"select count(*) from admin_terms_acceptance")));
        snapshot.put("ledgerSha256", digestRows(connection,"select user_id,role,created_at from pilot_account_creation order by user_id"));
        snapshot.put("acceptancesSha256", digestRows(connection,"select id,admin_user_id,organization_id,document_id,document_version,content_sha256,accepted_at from admin_terms_acceptance order by id"));
        phase="SNAPSHOT_HUMAN_REFERENCES";
        snapshot.put("humanFixturesSha256", digestRows(connection,"select id,organization_id,role,active,email_verified_at,session_version from app_user where id in ('"+OWNER+"','"+MANUAL_ADMIN+"') order by id"));
        phase="SNAPSHOT_PUBLISHED_TERMS";
        String publishedHash = scalar(connection,"select content_sha256 from admin_terms_document where document_key='ADMIN_TERMS' and version='1.0.0' and not synthetic");
        if (!PUBLISHED_HASH.equals(publishedHash)) throw new Refused("PUBLISHED_TERMS_MISMATCH");
        snapshot.put("publishedTermsSha256",publishedHash);
        phase="SNAPSHOT_MANUAL_ACCEPTANCE";
        if (!"1".equals(scalar(connection,"select count(*) from admin_terms_acceptance where id='"+MANUAL_ACCEPTANCE+"' and admin_user_id='"+MANUAL_ADMIN+"' and organization_id='"+ORGANIZATION+"' and document_version='1.0.0' and content_sha256='"+PUBLISHED_HASH+"'")))
            throw new Refused("MANUAL_ACCEPTANCE_NOT_PRESERVED");
        if (!"2".equals(scalar(connection,"select count(*) from app_user where (id='"+OWNER+"' and role='SUPER_ADMIN') or (id='"+MANUAL_ADMIN+"' and role='ADMIN' and organization_id='"+ORGANIZATION+"')")))
            throw new Refused("HUMAN_CASE_ACCOUNTS_MISMATCH");
        phase="SNAPSHOT_L02_CLOSED_CASE";
        if(!"1".equals(scalar(connection,"select count(*) from app_user u join driver d on d.user_id=u.id where u.id='3fb1f499-81d2-45dd-8e60-b53f6eb7ff79' and d.id='5712d0db-e92b-4988-9809-a49d04a194ef' and not u.active and not d.active")))throw new Refused("L02_CLOSED_CASE_NOT_PRESERVED");
        snapshot.put("L02ClosedCaseSha256",digestRows(connection,"select u.id,u.active,u.session_version,d.id,d.active,r.id,r.status,r.personal_approved_at from app_user u join driver d on d.user_id=u.id join conductor_onboarding_request r on r.linked_user_id=u.id and r.linked_driver_id=d.id where u.id='3fb1f499-81d2-45dd-8e60-b53f6eb7ff79' order by r.id"));
        phase="VEHICLE_IDENTITY_COLLISIONS";
        String collisions=scalar(connection,"select count(*) from(select regexp_replace(upper(plate),'[^A-Z0-9]','','g') canonical from vehicle group by 1 having count(*)>1)s");
        if(!"0".equals(collisions))throw new Refused("VEHICLE_CANONICAL_COLLISIONS_REQUIRE_REVIEW");
        snapshot.put("vehicleCanonicalCollisions",0);
        snapshot.put("incidentCount",Integer.parseInt(scalar(connection,"select count(*) from incident")));
        snapshot.put("incidentsOriginalsSha256",digestRows(connection,"select id,organization_id,driver_id,assignment_id,reported_by,category,status,description,reported_at,follow_up_note,followed_up_by,followed_up_at,resolved_at,driver_closed_by,driver_closed_at,version,created_at,updated_at,execution_segment_id from incident order by id"));
        snapshot.put("announcementsSha256",digestRows(connection,"select id,organization_id,created_by,title,body,audience_type,audience_id,require_read_ack,created_at from announcement order by id"));
        snapshot.put("announcementReceiptsSha256",digestRows(connection,"select announcement_id,user_id,delivered_at,read_at from announcement_receipt order by announcement_id,user_id"));
        if(Integer.parseInt(version)>=44){
            snapshot.put("announcementRecipientCount",Integer.parseInt(scalar(connection,"select count(*) from announcement_recipient")));
            snapshot.put("noticeCount",Integer.parseInt(scalar(connection,"select count(*) from operation_notice")));
            snapshot.put("announcementRecipientsSha256",digestRows(connection,"select announcement_id,user_id,organization_id,driver_id,group_at_publication,assigned_at,version,withdrawn_at,withdrawn_by from announcement_recipient order by announcement_id,user_id"));
            snapshot.put("noticesSha256",digestRows(connection,"select id,event_id,organization_id,user_id,event_type,references_json,created_at,read_at,external_status,external_reason from operation_notice order by id"));
        }
        return snapshot;
    }

    static String digestRows(Connection connection,String sql) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (Statement statement=connection.createStatement(); ResultSet rows=statement.executeQuery(sql)) {
            int columns=rows.getMetaData().getColumnCount();
            while(rows.next()) {
                for(int column=1;column<=columns;column++) {
                    String text=Objects.toString(rows.getObject(column),"<NULL>");
                    byte[] encoded=text.getBytes(StandardCharsets.UTF_8);
                    digest.update(Integer.toString(encoded.length).getBytes(StandardCharsets.US_ASCII));
                    digest.update((byte)':');digest.update(encoded);digest.update((byte)'|');
                }
                digest.update((byte)'\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String scalar(Connection connection,String sql) throws SQLException {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql)) {
            if(!rows.next())return "";
            return Objects.toString(rows.getObject(1),"").strip();
        }
    }
    private static Connection connect(NativePostgresTestDatabase database,boolean migrator)throws Exception {
        return DriverManager.getConnection(value(database,migrator?"migratorUrl":"testUrl"),
                value(database,migrator?"migratorUsername":"testUsername"),value(database,migrator?"migratorPassword":"testPassword"));
    }
    private static String value(NativePostgresTestDatabase database,String name)throws Exception {
        Field field=NativePostgresTestDatabase.class.getDeclaredField(name);field.setAccessible(true);
        return (String)field.get(database);
    }
    private static void assertNoHumanMarker() {
        Path cwd=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for(Path marker:List.of(cwd.resolve(".local/l06-connected.active"),cwd.resolve("backend/.local/l06-connected.active")))
            if(Files.exists(marker))throw new Refused("L06_CONNECTED_MARKER_PRESENT");
        for(Path marker:List.of(cwd.resolve(".local/l05-connected.active"),cwd.resolve("backend/.local/l05-connected.active"),cwd.resolve(".local/l03-connected.active"),cwd.resolve("backend/.local/l03-connected.active"),cwd.resolve(".local/l04-connected.active"),cwd.resolve("backend/.local/l04-connected.active")))
            if(Files.exists(marker))throw new Refused("L03_CONNECTED_MARKER_PRESENT");
        for(Path marker:List.of(cwd.resolve(".local/l02-connected.active"),cwd.resolve("backend/.local/l02-connected.active"),cwd.resolve(".local/l01-human-trial.active"),cwd.resolve("backend/.local/l01-human-trial.active"),cwd.resolve("../backend/.local/l01-human-trial.active").normalize()))
            if(Files.exists(marker))throw new Refused("HUMAN_TRIAL_MARKER_PRESENT");
    }
    private static void silenceLogging() {
        if(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context)
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.ERROR);
    }
    private static final class Refused extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 4411792199725561630L;
        final String reason;
        Refused(String reason){super(reason);this.reason=reason;}
    }
}
