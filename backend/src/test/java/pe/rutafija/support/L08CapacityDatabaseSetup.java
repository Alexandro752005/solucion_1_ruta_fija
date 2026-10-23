package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Explicit setup for the new authorized CAP25 cluster; never uses the ordinary test loader. */
public final class L08CapacityDatabaseSetup {
    private static final String CASE = "860c1cdb-b062-469a-9a3f-156ea23bb656";
    private static final String URL = "jdbc:postgresql://127.0.0.1:55439/ruta_fija_test";
    private static final String PROPOSAL_SHA = "cda897e21d2063589d253dd6c4d18ec39811d11cb7231988e63f7195aa8df8fb";
    private static final ObjectMapper JSON = new ObjectMapper();
    private L08CapacityDatabaseSetup() { }

    public static void main(String[] args) {
        String phase = "AUTHORIZATION";
        try {
            silenceLogging();
            require(args.length == 1 && Set.of("--migrate-new-v50", "--verify-v50").contains(args[0]), "EXACT_ACTION_REQUIRED");
            require("L08_CAP25_01_AUTHORIZED".equals(env("RF_L08_CAP25_AUTHORIZATION")) && URL.equals(env("RF_L08_CAP25_URL")), "EXACT_CAP25_ENV_REQUIRED");
            Path root = Path.of(env("RF_L08_CAP25_ROOT")).toRealPath();
            var auth = JSON.readTree(Files.readAllBytes(root.resolve("docs/evidencia/L-08/CAP25-01-autorizacion.json")));
            require(CASE.equals(auth.path("caseId").asText()) && "L08-CAP25-01".equals(auth.path("id").asText())
                && "AUTHORIZED_NOT_EXECUTED".equals(auth.path("status").asText()) && auth.path("port").asInt() == 55439
                && "127.0.0.1".equals(auth.path("host").asText()) && "ruta_fija_test".equals(auth.path("database").asText())
                && PROPOSAL_SHA.equals(auth.path("proposalSha256").asText()), "EXACT_AUTH_RECORD_REQUIRED");
            require(PROPOSAL_SHA.equals(sha(Files.readAllBytes(root.resolve("docs/operacion/L-08-CAP25-01-validacion-aislada-propuesta.md")))), "PROPOSAL_CONTENT_UNCHANGED");
            var migrations = currentMigrationHashes(root);
            String v46 = resource("db/migration/V46__purpose_bound_retention.sql");
            String v49 = resource("db/migration/V49__purpose_bound_personal_retirement.sql");
            String runtimeBootstrap = resource("db/test-migration/R__native_test_role_privileges.sql");
            require(runtimeBootstrap.equals(Files.readString(root.resolve("backend/src/main/resources/db/test-migration/R__native_test_role_privileges.sql"))), "RUNTIME_GRANT_SOURCE_MATCH");
            Set<String> protectedTables = new TreeSet<>(); protectedTables.add("pilot_account_creation");
            protectedTables.addAll(inventory(v46, "protected_table", 9)); protectedTables.addAll(inventory(v49, "t", 7));
            require(protectedTables.size() == 17, "PROTECTED_TABLE_INVENTORY_BOUNDARY");
            var authorities = Set.of("retention_execution_authority", "retention_personal_row_authority");
            require(protectedTables.containsAll(authorities), "PRIVATE_AUTHORITIES_PRESENT");
            boolean migrate = args[0].equals("--migrate-new-v50");
            phase = "EXACT_DATABASE_AND_ROLE_BEFORE_MIGRATION";
            try (var c = connect("rf_migrator")) {
                assertTarget(c, "rf_migrator");
                if (migrate) {
                    require(number(c, "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in('r','p','v','m','S','f')") == 0, "NEW_EMPTY_PUBLIC_SCHEMA_REQUIRED_NO_REPLAY");
                    // Apply the existing test grant bootstrap BEFORE versioned migrations.
                    // Its ALTER DEFAULT PRIVILEGES supplies ordinary DML to future tables;
                    // V22/V46/V49 then revoke their protected tables/functions normally.
                    // It must never run as a repeatable after those revocations.
                    phase = "EXISTING_TEST_GRANT_BOOTSTRAP_BEFORE_VERSIONED_MIGRATIONS";
                    c.setAutoCommit(false);
                    try (var statement = c.createStatement()) { statement.execute(runtimeBootstrap); c.commit(); }
                    catch (Exception failure) { c.rollback(); throw failure; }
                }
            }
            var flyway = Flyway.configure().dataSource(URL, "rf_migrator", env("RF_L08_CAP25_MIGRATOR_PASSWORD"))
                .locations("classpath:db/migration").target("50").cleanDisabled(true).connectRetries(0)
                .validateMigrationNaming(true).initSql("SET statement_timeout='120s'; SET lock_timeout='5s'").load();
            int executed = 0;
            if (migrate) {
                phase = "MIGRATE_EXACT_VERSIONED_V1_TO_V50";
                executed = flyway.migrate().migrationsExecuted;
                require(executed == 50, "EXACTLY_50_VERSIONED_MIGRATIONS_REQUIRED");
            }
            phase = "FLYWAY_VALIDATE_AND_RUNTIME_BOUNDARIES";
            var validation = flyway.validateWithResult(); require(validation.validationSuccessful, "FLYWAY_VALIDATION_REQUIRED");
            Map<String, Integer> ledger;
            int tableCount;
            try (var c = connect("rf_migrator")) {
                assertTarget(c, "rf_migrator");
                require(number(c, "select count(*) from flyway_schema_history where not success") == 0
                    && number(c, "select count(*) from flyway_schema_history where version is not null") == 50
                    && number(c, "select count(*) from flyway_schema_history where version is null") == 0
                    && "50".equals(text(c, "select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1")), "EXACT_VERSIONED_HISTORY_50_R0");
                tableCount = (int) number(c, "select count(*) from pg_tables where schemaname='public'");
                for (String table : protectedTables) for (String privilege : List.of("INSERT", "UPDATE", "DELETE", "TRUNCATE"))
                    for (String role : List.of("rf_test", "rf_app")) require(!tablePrivilege(c, role, table, privilege), "NO_DIRECT_PROTECTED_MUTATION");
                for (String table : authorities) for (String role : List.of("rf_test", "rf_app")) require(!tablePrivilege(c, role, table, "SELECT"), "PRIVATE_AUTHORITY_INACCESSIBLE");
                require(number(c, "select count(*) from pg_roles where rolname in('rf_test','rf_migrator') and (rolsuper or rolcreaterole or rolcreatedb or rolreplication or rolbypassrls)") == 0, "NO_PRIVILEGED_APPLICATION_ROLES");
                require(number(c, "select count(*) from pg_auth_members m join pg_roles r on r.oid=m.member where r.rolname in('rf_test','rf_migrator')") == 0, "NO_PRIVILEGED_ROLE_MEMBERSHIPS");
                require(number(c, "select count(*) from pg_proc p where p.pronamespace='public'::regnamespace and p.proname in('retention_personal_apply','retention_prepare_personal_plan_internal') and has_function_privilege('rf_test',p.oid,'EXECUTE')") == 0, "PRIVATE_RETENTION_HELPERS_NOT_CALLABLE");
                ledger = new TreeMap<>(); for (String role : List.of("SUPER_ADMIN", "ADMIN", "CONDUCTOR")) {
                    try (var s = c.prepareStatement("select count(*) from pilot_account_creation where role=?")) {
                        s.setString(1, role); try (var r = s.executeQuery()) { r.next(); ledger.put(role, r.getInt(1)); }
                    }
                }
                require(ledger.get("SUPER_ADMIN") <= 1 && ledger.get("ADMIN") <= 4 && ledger.get("CONDUCTOR") <= 20, "HISTORICAL_CAPS_PRESERVED");
                if (migrate) require(ledger.values().stream().allMatch(n -> n == 0), "NEW_DATABASE_NO_ACCOUNTS_CREATED_BY_SETUP");
            }
            try (var c = connect("rf_test")) {
                assertTarget(c, "rf_test");
                require(!bool(c, "select has_schema_privilege(current_user,'public','CREATE')"), "RUNTIME_NO_SCHEMA_DDL");
                for (String table : List.of("organization", "app_user", "driver", "vehicle", "assignment"))
                    for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) require(tablePrivilege(c, "rf_test", table, privilege), "NORMAL_RUNTIME_DML_PRESENT");
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("result", "PASS_CAP25_ISOLATED_V50_SCHEMA_AND_ROLE_BOUNDARIES"); result.put("caseId", CASE);
            result.put("observedAt", Instant.now().toString()); result.put("database", "ruta_fija_test"); result.put("port", 55439);
            result.put("schemaVersion", "50"); result.put("versionedMigrations", 50); result.put("repeatableMigrations", 0);
            result.put("migrationsExecutedThisInvocation", executed); result.put("flywayValidationSuccessful", true);
            result.put("existingTestGrantBootstrapAppliedBeforeVersionedMigrations", true);
            result.put("testGrantBootstrapSha256", sha(runtimeBootstrap.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            result.put("grantBootstrapSeparateFromMigrationHistory", true); result.put("repeatableAfterProtectedRevocationsNotExecuted", true);
            result.put("protectedTableInventoryDerivedFromV22V46V49", protectedTables); result.put("protectedTableDirectDmlDenied", true);
            result.put("privateAuthoritiesSelectDenied", true); result.put("runtimeDmlAndNoDdlVerified", true);
            result.put("migrationSourceSha256", migrations); result.put("tableCount", tableCount); result.put("ledger", ledger);
            result.put("newSyntheticAccountsCreatedBySetup", 0); result.put("source5432Accessed", false); result.put("restore55438Accessed", false);
            System.out.println(JSON.writeValueAsString(result));
        } catch (Exception failure) {
            var result = new LinkedHashMap<String, Object>(); result.put("result", "STOPPED_KEEP_CAP25_CASE_NO_RESET");
            result.put("failurePhase", phase); result.put("failureType", failure.getClass().getSimpleName());
            if (failure instanceof java.sql.SQLException sql && sql.getSQLState() != null) result.put("sqlState", sql.getSQLState());
            if (failure instanceof IllegalStateException && failure.getMessage() != null && failure.getMessage().matches("[A-Z][A-Z0-9_]{2,100}")) result.put("check", failure.getMessage());
            try { System.out.println(JSON.writeValueAsString(result)); } catch (Exception ignored) { }
            System.exit(1);
        }
    }

    private static Map<String, String> currentMigrationHashes(Path root) throws Exception {
        var result = new TreeMap<String, String>(); var versions = new TreeSet<Integer>();
        try (var files = Files.list(root.resolve("backend/src/main/resources/db/migration"))) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                var m = Pattern.compile("V([0-9]+)__.+\\.sql").matcher(p.getFileName().toString());
                require(m.matches(), "ONLY_VERSIONED_MIGRATIONS_ALLOWED");
                int v = Integer.parseInt(m.group(1)); require(v >= 1 && v <= 50 && versions.add(v), "UNIQUE_V1_V50_ONLY");
                byte[] disk = Files.readAllBytes(p); byte[] compiled;
                try (var stream = L08CapacityDatabaseSetup.class.getClassLoader().getResourceAsStream("db/migration/" + p.getFileName())) { require(stream != null, "COMPILED_MIGRATION_REQUIRED"); compiled = stream.readAllBytes(); }
                require(MessageDigest.isEqual(disk, compiled), "COMPILED_MIGRATION_MUST_MATCH_CURRENT_SOURCE"); result.put(p.getFileName().toString(), sha(disk));
            }
        }
        require(versions.size() == 50, "FIFTY_CURRENT_MIGRATIONS_REQUIRED"); return result;
    }
    private static Set<String> inventory(String sql, String variable, int expected) {
        var m = Pattern.compile("FOREACH\\s+" + variable + "\\s+IN\\s+ARRAY\\s+ARRAY\\[([^]]+)\\]\\s+LOOP\\s+EXECUTE\\s+format\\('REVOKE INSERT", Pattern.CASE_INSENSITIVE).matcher(sql);
        require(m.find(), "MIGRATION_PROTECTION_INVENTORY_REQUIRED");
        var result = new TreeSet<String>();
        for (String item : m.group(1).split(",")) { String name = item.strip(); require(name.matches("'[a-z_][a-z0-9_]*'"), "SAFE_MIGRATION_TABLE_INVENTORY"); result.add(name.substring(1, name.length() - 1)); }
        require(result.size() == expected && !m.find(), "EXACT_MIGRATION_TABLE_INVENTORY"); return result;
    }
    private static Connection connect(String role) throws Exception {
        var p = new Properties(); p.setProperty("user", role); p.setProperty("password", env(role.equals("rf_test") ? "RF_L08_CAP25_TEST_PASSWORD" : "RF_L08_CAP25_MIGRATOR_PASSWORD"));
        p.setProperty("connectTimeout", "5"); p.setProperty("socketTimeout", "180"); p.setProperty("ApplicationName", "RutaFija-L08-CAP25-setup");
        p.setProperty("options", "-c statement_timeout=120000 -c lock_timeout=5000");
        try { return DriverManager.getConnection(URL, p); } finally { p.clear(); }
    }
    private static void assertTarget(Connection c, String role) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery("select current_database(),current_user,inet_server_port(),datcollate,datctype,host(inet_server_addr()),pg_get_userbyid(datdba) from pg_database where datname=current_database()")) {
            require(r.next() && "ruta_fija_test".equals(r.getString(1)) && role.equals(r.getString(2)) && r.getInt(3) == 55439
                && "Spanish_Peru.1252".equals(r.getString(4)) && "Spanish_Peru.1252".equals(r.getString(5))
                && "127.0.0.1".equals(r.getString(6)) && "rf_migrator".equals(r.getString(7)), "EXACT_CAP25_DATABASE_ROLE_REGION");
        }
        require("80".equals(text(c, "show max_connections")) && "on".equals(text(c, "show fsync"))
            && "on".equals(text(c, "show synchronous_commit")) && "on".equals(text(c, "show full_page_writes")), "DURABLE_SERVER_SETTINGS_REQUIRED");
    }
    private static boolean tablePrivilege(Connection c, String role, String table, String privilege) throws Exception {
        try (var s = c.prepareStatement("select has_table_privilege(?, ?, ?)")) { s.setString(1, role); s.setString(2, "public." + table); s.setString(3, privilege); try (var r = s.executeQuery()) { r.next(); return r.getBoolean(1); } }
    }
    private static long number(Connection c, String sql) throws Exception { try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getLong(1); } }
    private static String text(Connection c, String sql) throws Exception { try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getString(1); } }
    private static boolean bool(Connection c, String sql) throws Exception { try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getBoolean(1); } }
    private static String resource(String name) throws Exception { try (var input = L08CapacityDatabaseSetup.class.getClassLoader().getResourceAsStream(name)) { require(input != null, "MIGRATION_RESOURCE_REQUIRED"); return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); } }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String env(String name) { String value = System.getenv(name); require(value != null && !value.isBlank(), "PRIVATE_ENV_INPUT_REQUIRED"); return value; }
    private static void require(boolean value, String check) { if (!value) throw new IllegalStateException(check); }
    private static void silenceLogging() {
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext loggers)
            loggers.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
    }
}
