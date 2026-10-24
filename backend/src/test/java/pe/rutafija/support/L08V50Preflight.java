package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Explicit L08 test-only preflight. Does not start the application, send mail,
 * clean, repair, purge, change data to recover quota, or access development/pilot.
 * DDL rehearsal is rollback-only; migration is the separate, explicit mode. */
public final class L08V50Preflight {
    private static final String SCRIPT="V50__retired_owner_trigger_record_projection.sql";
    private static final String EXPECTED_V50_SHA256="2b7088c94e8b81219e7dd0d0ae4d570ba493c2c0c6f162a30494c99dd167f843";
    private static final String TERMS_SHA256="20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417";
    private static final long LOCK_KEY=7_518_440_514L;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final List<String> NEW_TABLES=List.of("retention_personal_delegation","retention_personal_plan_delegation",
        "retention_personal_hold_request","retention_personal_hold_approval","retention_personal_retirement",
        "retention_personal_row_authority","retention_personal_capability");
    private static final Set<String> PRIVATE_AUTHORITIES=Set.of("retention_execution_authority","retention_personal_row_authority");
    private static final List<String> MODES=List.of("--read-only","--rehearse-v50","--migrate-v50","--validate-v50");
    private static String phase="ARGUMENTS";
    private static boolean migrationExecutionStarted;
    private static Map<String,Object> validationDiagnostics=Map.of();
    private L08V50Preflight(){ }

    public static void main(String[] args){
        try{
            silenceLogging();
            if(args.length!=1||!MODES.contains(args[0]))throw refused("UNSUPPORTED_ARGUMENTS");
            String mode=args[0];boolean rehearsing="--rehearse-v50".equals(mode),migrating="--migrate-v50".equals(mode),validating="--validate-v50".equals(mode);
            phase="SOURCE_IDENTITY";Path root=workspaceRoot();byte[] source=Files.readAllBytes(root.resolve("backend/src/main/resources/db/migration/"+SCRIPT));
            if(!EXPECTED_V50_SHA256.equals(sha(source)))throw refused("V50_SOURCE_HASH_MISMATCH");
            try(InputStream resource=L08V50Preflight.class.getClassLoader().getResourceAsStream("db/migration/"+SCRIPT)){
                if(resource==null||!EXPECTED_V50_SHA256.equals(sha(resource.readAllBytes())))throw refused("V50_COMPILED_RESOURCE_HASH_MISMATCH");
            }
            phase="PRIVATE_TEST_CONFIGURATION";NativePostgresTestDatabase database=NativePostgresTestDatabase.load();
            var config=new CapturedProperties();database.registerSpringProperties(config);
            phase="ACTIVE_MARKERS";assertNoActiveMarker(root);
            phase="TEST_ROLE_PERMISSIONS";database.assertTestRolePermissions();
            try(Connection observer=connect(config,false)){
                observer.setAutoCommit(false);observer.setReadOnly(true);assertTarget(observer,"rf_test");
                phase="PROTECTION_LOCK";
                if(!Boolean.parseBoolean(scalar(observer,"select pg_try_advisory_lock("+LOCK_KEY+")")))throw refused("CLEANUP_PROTECTION_LOCK_HELD");
                try{
                    assertNoActiveMarker(root);
                    phase="SNAPSHOT_BEFORE";Snapshot before=snapshot(observer,null);Map<String,Object> filesBefore=historicalFiles(root);
                    if((rehearsing||migrating)&&!"49".equals(before.version))throw refused("V50_REQUIRES_CURRENT_SCHEMA_49");
                    if(validating&&!"50".equals(before.version))throw refused("VALIDATION_REQUIRES_CURRENT_SCHEMA_49");
                    if((rehearsing||migrating)&&(!Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",6).equals(before.summary.get("ledger"))
                        ||!Integer.valueOf(2).equals(before.summary.get("acceptanceCount"))))throw refused("PRE_MIGRATION_BASELINE_CHANGED_REVIEW_REQUIRED");
                    Map<String,Object> output=new LinkedHashMap<>();output.put("observedAt",Instant.now().toString());output.put("mode",mode);
                    output.put("database","ruta_fija_test");output.put("observerRole","rf_test");output.put("sourceV50Sha256",EXPECTED_V50_SHA256);
                    output.put("before",before.summary);output.put("historicalFilesBefore",filesBefore);
                    if(rehearsing||migrating){
                        phase="MIGRATOR_TARGET_CHECK";
                        try(Connection migrator=connect(config,true)){
                            assertTarget(migrator,"rf_migrator");
                            if(!"0".equals(scalar(migrator,"select count(*) from retention_execution_authority")))throw refused("EXISTING_EXECUTION_AUTHORITY_PRESENT");
                        }
                        phase="VALIDATE_PENDING_V50";Flyway flyway=flyway(config,true);assertOnlyV50Pending(flyway);
                        output.put("flywayValidationBefore",validationDiagnostics);
                        // A read transaction keeps relation locks. Release those before DDL;
                        // this session's cleanup-protection advisory lock remains held.
                        observer.rollback();assertNoActiveMarker(root);
                        if(rehearsing){
                            phase="REHEARSE_SCRIPT_EXECUTION";
                            try(Connection migrator=connect(config,true)){
                                migrator.setAutoCommit(false);
                                try{
                                    assertTarget(migrator,"rf_migrator");setTimeouts(migrator);
                                    try(Statement statement=migrator.createStatement()){
                                        statement.setQueryTimeout(120);statement.execute(new String(source,StandardCharsets.UTF_8));
                                    }
                                    phase="REHEARSE_PRESERVATION_INSIDE_TRANSACTION";
                                    Snapshot staged=snapshot(migrator,before.columns);assertPreservedData(before,staged);
                                    assertNewV50Metadata(migrator);assertNoInventedCancellationAnchor(migrator);
                                    if(!"49".equals(staged.version)||!before.historySha256.equals(staged.historySha256))throw refused("REHEARSAL_MUST_NOT_WRITE_FLYWAY_HISTORY");
                                    output.put("stagedPersonalMetadata","SEVEN_TABLES_FIVE_CAPABILITIES_ZERO_EXECUTIONS_OR_AUTHORITIES");
                                }finally{migrator.rollback();}
                            }
                            observer.rollback();phase="REHEARSE_ROLLBACK_VERIFICATION";Snapshot after=snapshot(observer,null);
                            assertPreservedData(before,after);
                            if(!before.schemaSha256.equals(after.schemaSha256)||!before.historySha256.equals(after.historySha256)
                                ||!before.columns.equals(after.columns))throw refused("ROLLBACK_SCHEMA_OR_HISTORY_MISMATCH");
                            assertHistoricalFilesUnchanged(filesBefore,historicalFiles(root));
                            output.put("after",after.summary);output.put("rehearsalRollback","PASS");
                            output.put("sourceExecutedWithinRollbackOnly",true);output.put("migrationApplied",false);
                        }else{
                            phase="FLYWAY_MIGRATE_V50";migrationExecutionStarted=true;
                            var migrated=flyway.migrate();
                            if(migrated.migrationsExecuted!=1)throw refused("EXPECTED_EXACTLY_ONE_VERSIONED_MIGRATION");
                            observer.rollback();phase="POST_MIGRATION_PRESERVATION";Snapshot after=snapshot(observer,before.columns);
                            if(!"50".equals(after.version))throw refused("TARGET_49_NOT_APPLIED");
                            assertPreservedData(before,after);assertHistoryExtendedOnlyBy50(observer,before);
                            try(Connection migrator=connect(config,true)){assertTarget(migrator,"rf_migrator");assertNewV50Metadata(migrator);assertNoInventedCancellationAnchor(migrator);}
                            phase="POST_MIGRATION_FLYWAY_VALIDATION";var validation=flyway(config,false).validateWithResult();validationDiagnostics=validationSummary(validation);
                            if(!validation.validationSuccessful)throw refused("POST_MIGRATION_FLYWAY_VALIDATION_FAILED");
                            assertHistoricalFilesUnchanged(filesBefore,historicalFiles(root));
                            output.put("after",after.summary);output.put("migrationsExecuted",migrated.migrationsExecuted);
                            output.put("flywayValidationAfter",validationDiagnostics);output.put("preservation","PASS");output.put("migrationApplied",true);
                        }
                    }else if(validating){
                        phase="FLYWAY_READ_ONLY_VALIDATION";var validation=flyway(config,false).validateWithResult();validationDiagnostics=validationSummary(validation);
                        if(!validation.validationSuccessful)throw refused("FLYWAY_VALIDATION_FAILED");
                        output.put("flywayValidation",validationDiagnostics);
                        assertHistoricalFilesUnchanged(filesBefore,historicalFiles(root));output.put("migrationApplied",false);
                    }else output.put("migrationApplied",false);
                    output.put("globalCleanupExecuted",false);output.put("personalRetirementExecuted",false);output.put("developmentOrPilotAccessed",false);
                    output.put("result","PASS");phase="SANITIZED_OUTPUT";System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(output));
                }finally{
                    observer.rollback();scalar(observer,"select pg_advisory_unlock("+LOCK_KEY+")");observer.rollback();
                }
            }
        }catch(Throwable failure){
            Map<String,Object> output=new LinkedHashMap<>();output.put("result","REFUSED");output.put("phase",phase);
            output.put("reason",failure instanceof Refused r?r.code:"OPERATION_FAILED_DETAILS_REDACTED");
            output.put("migrationExecutionStarted",migrationExecutionStarted);
            Throwable cause=failure;for(int depth=0;depth<20&&cause.getCause()!=null;depth++)cause=cause.getCause();
            output.put("causeType",cause.getClass().getSimpleName());output.put("sqlState",sqlState(failure));
            output.putAll(functionLocation(failure));if(!validationDiagnostics.isEmpty())output.put("flywayValidation",validationDiagnostics);
            try{System.out.println(JSON.writeValueAsString(output));}catch(Exception ignored){System.out.println("{\"result\":\"REFUSED\",\"reason\":\"SANITIZED_OUTPUT_FAILURE\"}");}
            System.exit(1);
        }
    }

    private record Snapshot(String version,Map<String,List<String>> columns,Map<String,Object> summary,String schemaSha256,String historySha256,int historyMaxRank){ }
    private static Snapshot snapshot(Connection c,Map<String,List<String>> projection)throws Exception{
        String version=scalar(c,"select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1");
        if(!Set.of("49","50").contains(version)||!"0".equals(scalar(c,"select count(*) from flyway_schema_history where not success")))throw refused("UNEXPECTED_SCHEMA_HISTORY");
        Map<String,List<String>> allColumns=columns(c);Map<String,List<String>> selected=projection==null?allColumns:projection;
        assertPrivateAuthorityPermissions(c,"50".equals(version));
        Map<String,Object> tables=new LinkedHashMap<>();
        for(var table:selected.entrySet()){
            // A normal app role must not gain SELECT over the private authority
            // tables for diagnostic convenience. The explicit migrator checks
            // their emptiness separately in both write-capable modes.
            if(PRIVATE_AUTHORITIES.contains(table.getKey()))continue;
            if(!allColumns.containsKey(table.getKey())||!allColumns.get(table.getKey()).containsAll(table.getValue()))throw refused("EXISTING_TABLE_OR_COLUMN_MISSING");
            String cols=String.join(",",table.getValue().stream().map(L08V50Preflight::quoted).toList());
            String qualified="public."+quoted(table.getKey());
            // Hash each row inside PostgreSQL: credentials/PII are never returned
            // to the preflight process, logs, or evidence, even transiently.
            String query="select encode(sha256(convert_to(to_jsonb(x)::text,'UTF8')),'hex') row_sha from(select "+cols+" from "+qualified+") x order by row_sha";
            tables.put(table.getKey(),Map.of("count",Long.parseLong(scalar(c,"select count(*) from "+qualified)),"sha256",L07DatabasePreflight.digestRows(c,query)));
        }
        Map<String,Integer> ledger=new LinkedHashMap<>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR")){
            int count=Integer.parseInt(scalar(c,"select count(*) from pilot_account_creation where role='"+role+"'"));int limit=role.equals("SUPER_ADMIN")?1:role.equals("ADMIN")?4:20;
            if(count>limit)throw refused("HISTORICAL_CREATION_QUOTA_EXCEEDED");ledger.put(role,count);
        }
        String published=scalar(c,"select content_sha256 from admin_terms_document where document_key='ADMIN_TERMS' and version='1.0.0' and not synthetic");
        if(!TERMS_SHA256.equals(published))throw refused("PUBLISHED_TERMS_MISMATCH");
        if(!"1".equals(scalar(c,"""
            select count(*) from admin_terms_acceptance where id='c496b63c-735f-48cc-be56-90f7ec1464e1'
            and admin_user_id='92ebc07c-1319-4850-a734-f792cb299979' and organization_id='1a759298-7831-4aaf-9299-c3920a9cc28a'
            and document_version='1.0.0' and content_sha256='20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417'
            """)))throw refused("EXISTING_MANUAL_ACCEPTANCE_NOT_PRESERVED");
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("schemaVersion",version);summary.put("ledger",ledger);
        summary.put("acceptanceCount",Integer.parseInt(scalar(c,"select count(*) from admin_terms_acceptance")));
        summary.put("publishedTermsSha256",published);summary.put("tables",tables);
        summary.put("privateAuthorityTablesExcludedFromObserverRead",selected.keySet().stream().filter(PRIVATE_AUTHORITIES::contains).toList());
        summary.put("historyRows",Integer.parseInt(scalar(c,"select count(*) from flyway_schema_history")));
        summary.put("successfulVersionedHistoryRows",Integer.parseInt(scalar(c,"select count(*) from flyway_schema_history where success and version is not null")));
        summary.put("successfulRepeatableHistoryRows",Integer.parseInt(scalar(c,"select count(*) from flyway_schema_history where success and version is null")));
        String history=L07DatabasePreflight.digestRows(c,"select installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success from flyway_schema_history order by installed_rank");
        String schema=schemaDigest(c);summary.put("historySha256",history);summary.put("schemaSha256",schema);
        return new Snapshot(version,allColumns,summary,schema,history,Integer.parseInt(scalar(c,"select coalesce(max(installed_rank),0) from flyway_schema_history")));
    }
    private static Map<String,List<String>> columns(Connection c)throws SQLException{
        Map<String,List<String>> out=new LinkedHashMap<>();
        try(Statement q=c.createStatement();ResultSet rows=q.executeQuery("""
            select c.relname,a.attname from pg_class c join pg_namespace n on n.oid=c.relnamespace
              join pg_attribute a on a.attrelid=c.oid and a.attnum>0 and not a.attisdropped
             where n.nspname='public' and c.relkind='r' and c.relname<>'flyway_schema_history' order by c.relname,a.attnum
            """)){while(rows.next())out.computeIfAbsent(rows.getString(1),k->new ArrayList<>()).add(rows.getString(2));}
        return out;
    }
    private static String schemaDigest(Connection c)throws Exception{
        return L07DatabasePreflight.digestRows(c,"""
            select kind,identity,definition from(
              select 'COLUMN' kind,c.relname||'.'||a.attname identity,
                format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') definition
                from pg_class c join pg_namespace n on n.oid=c.relnamespace join pg_attribute a on a.attrelid=c.oid and a.attnum>0 and not a.attisdropped
                left join pg_attrdef d on d.adrelid=c.oid and d.adnum=a.attnum where n.nspname='public' and c.relkind='r'
              union all select 'CONSTRAINT',c.conrelid::regclass::text||'.'||c.conname,pg_get_constraintdef(c.oid)
                from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='public'
              union all select 'TRIGGER',t.tgrelid::regclass::text||'.'||t.tgname,pg_get_triggerdef(t.oid)
                from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and not t.tgisinternal
              union all select 'FUNCTION',p.oid::regprocedure::text,pg_get_functiondef(p.oid)
                from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.prokind='f'
              union all select 'INDEX',i.indexrelid::regclass::text,pg_get_indexdef(i.indexrelid)
                from pg_index i join pg_class c on c.oid=i.indrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public'
              union all select 'TABLE_ACL',c.relname,coalesce(c.relacl::text,'')
                from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in('r','S','v','m')
              union all select 'FUNCTION_ACL',p.oid::regprocedure::text,coalesce(p.proacl::text,'')
                from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public'
              union all select 'SCHEMA_ACL',n.nspname,coalesce(n.nspacl::text,'') from pg_namespace n where n.nspname='public'
            ) x order by kind,identity
            """);
    }
    @SuppressWarnings("unchecked") private static void assertPreservedData(Snapshot before,Snapshot after){
        for(String key:List.of("ledger","acceptanceCount","publishedTermsSha256","tables"))
            if(!Objects.equals(before.summary.get(key),after.summary.get(key)))throw refused("PRESERVATION_MISMATCH_"+key.toUpperCase(Locale.ROOT));
    }
    private static void assertHistoryExtendedOnlyBy50(Connection c,Snapshot before)throws Exception{
        String old=L07DatabasePreflight.digestRows(c,"select installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success from flyway_schema_history where installed_rank<="+before.historyMaxRank+" order by installed_rank");
        if(!before.historySha256.equals(old)||!"1".equals(scalar(c,"select count(*) from flyway_schema_history where installed_rank>"+before.historyMaxRank+" and version='50' and success"))
            ||!"1".equals(scalar(c,"select count(*) from flyway_schema_history where installed_rank>"+before.historyMaxRank)))throw refused("HISTORY_NOT_EXACTLY_EXTENDED_BY_V50");
    }
    private static void assertNewV50Metadata(Connection c)throws SQLException{
        assertPrivateAuthorityPermissions(c,true);
        for(String table:NEW_TABLES){String expected=table.equals("retention_personal_capability")?"5":"0";
            if(!expected.equals(scalar(c,"select count(*) from public."+quoted(table))))throw refused("UNEXPECTED_NEW_V50_METADATA");}
        if(!"0".equals(scalar(c,"select count(*) from retention_execution_authority")))throw refused("EXISTING_EXECUTION_AUTHORITY_PRESENT");
    }
    private static void assertPrivateAuthorityPermissions(Connection c,boolean personalExists)throws SQLException{
        for(String role:List.of("rf_test","rf_app")){
            if(!Boolean.parseBoolean(scalar(c,"select exists(select 1 from pg_roles where rolname='"+role+"')")))continue;
            for(String table:personalExists?List.of("retention_execution_authority","retention_personal_row_authority"):List.of("retention_execution_authority"))
                if(Boolean.parseBoolean(scalar(c,"select has_table_privilege('"+role+"','public."+table+"','SELECT,INSERT,UPDATE,DELETE,TRUNCATE')")))throw refused("PRIVATE_AUTHORITY_PERMISSION_LEAK");
            if(personalExists&&Boolean.parseBoolean(scalar(c,"select exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in('retention_personal_rows','retention_personal_fingerprint','retention_personal_lock_inventory','retention_prepare_personal_plan_internal') and has_function_privilege('"+role+"',p.oid,'EXECUTE'))")))throw refused("PRIVATE_RETIREMENT_HELPER_EXECUTE_LEAK");
        }
    }
    private static void assertNoInventedCancellationAnchor(Connection c)throws SQLException{
        if(!"0".equals(scalar(c,"select count(*) from conductor_recovery_request where cancelled_at is not null")))throw refused("CANCELLATION_ANCHOR_BACKFILL_OR_CONCURRENT_WRITE");
    }
    private static void assertOnlyV50Pending(Flyway flyway){
        var validation=flyway.validateWithResult();validationDiagnostics=validationSummary(validation);
        boolean expectedPending=!validation.validationSuccessful&&validation.invalidMigrations.size()==1
            &&"50".equals(validation.invalidMigrations.getFirst().version)
            &&validation.invalidMigrations.getFirst().errorDetails!=null
            &&"RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(validation.invalidMigrations.getFirst().errorDetails.errorCode.toString());
        if(!validation.validationSuccessful&&!expectedPending)throw refused("FLYWAY_EXISTING_HISTORY_VALIDATION_FAILED");
        var pending=flyway.info().pending();
        if(pending.length!=1||pending[0].getVersion()==null||!"50".equals(pending[0].getVersion().getVersion())||!SCRIPT.equals(pending[0].getScript()))throw refused("ONLY_PENDING_V50_REQUIRED");
    }
    private static Map<String,Object> validationSummary(ValidateResult validation){
        var out=new LinkedHashMap<String,Object>();out.put("successful",validation.validationSuccessful);out.put("validatedCount",validation.validateCount);
        out.put("code",validation.errorDetails==null?"NONE":validation.errorDetails.errorCode.toString());
        out.put("invalidMigrations",validation.invalidMigrations.stream().map(item->Map.of("version",Objects.toString(item.version,""),"code",item.errorDetails==null?"NONE":item.errorDetails.errorCode.toString())).toList());return out;
    }
    private static Flyway flyway(CapturedProperties config,boolean migrator){
        return Flyway.configure().dataSource(config.value(migrator?"RF_TEST_MIGRATOR_URL":"RF_TEST_DATASOURCE_URL"),config.value(migrator?"RF_TEST_MIGRATOR_USERNAME":"RF_TEST_DATASOURCE_USERNAME"),config.value(migrator?"RF_TEST_MIGRATOR_PASSWORD":"RF_TEST_DATASOURCE_PASSWORD"))
            .locations("classpath:db/migration","classpath:db/test-migration").target("50").cleanDisabled(true).connectRetries(0)
            .initSql("SET statement_timeout='120s'; SET lock_timeout='5s'").load();
    }
    private static Connection connect(CapturedProperties config,boolean migrator)throws SQLException{
        var props=new Properties();props.setProperty("user",config.value(migrator?"RF_TEST_MIGRATOR_USERNAME":"RF_TEST_DATASOURCE_USERNAME"));props.setProperty("password",config.value(migrator?"RF_TEST_MIGRATOR_PASSWORD":"RF_TEST_DATASOURCE_PASSWORD"));
        props.setProperty("connectTimeout","10");props.setProperty("socketTimeout","180");props.setProperty("ApplicationName","RutaFija-L08-ExactPreflight");
        return DriverManager.getConnection(config.value(migrator?"RF_TEST_MIGRATOR_URL":"RF_TEST_DATASOURCE_URL"),props);
    }
    private static void setTimeouts(Connection c)throws SQLException{try(Statement q=c.createStatement()){q.execute("set local statement_timeout='120s'");q.execute("set local lock_timeout='5s'");}}
    private static void assertTarget(Connection c,String role)throws SQLException{
        if(!"ruta_fija_test".equals(scalar(c,"select current_database()"))||!role.equals(scalar(c,"select current_user"))
            ||!role.equals(scalar(c,"select session_user"))||!"5432".equals(scalar(c,"select inet_server_port()")))throw refused("EXACT_TEST_TARGET_OR_ROLE_MISMATCH");
    }
    private static String scalar(Connection c,String sql)throws SQLException{try(Statement q=c.createStatement();ResultSet r=q.executeQuery(sql)){return r.next()?Objects.toString(r.getObject(1),"").strip():"";}}
    private static String quoted(String name){if(!name.matches("[a-z][a-z0-9_]*"))throw refused("UNEXPECTED_SCHEMA_IDENTIFIER");return '"'+name+'"';}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static Path workspaceRoot()throws Exception{
        Path cwd=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for(Path candidate:Arrays.asList(cwd,cwd.getParent()))if(candidate!=null&&Files.isRegularFile(candidate.resolve("backend/pom.xml"))&&Files.isDirectory(candidate.resolve("docs/contratos")))return candidate.toRealPath();
        throw refused("PROJECT_ROOT_NOT_IDENTIFIED");
    }
    private static void assertNoActiveMarker(Path root)throws Exception{
        for(Path folder:List.of(root.resolve(".local"),root.resolve("backend/.local"))){
            if(!Files.isDirectory(folder))continue;
            try(var paths=Files.list(folder)){if(paths.anyMatch(p->p.getFileName().toString().endsWith(".active")))throw refused("ACTIVE_CASE_MARKER_PRESENT");}
        }
    }
    private static Map<String,Object> historicalFiles(Path root)throws Exception{
        MessageDigest all=MessageDigest.getInstance("SHA-256");List<Path> selected=new ArrayList<>();
        for(int lot=0;lot<=7;lot++){Path folder=root.resolve("docs/evidencia/L-"+String.format(Locale.ROOT,"%02d",lot));if(Files.isDirectory(folder))try(var paths=Files.walk(folder)){selected.addAll(paths.filter(Files::isRegularFile).toList());}}
        for(String relative:List.of("backups/L-00-2026-10-13-final3/source-snapshot.zip","docs/legal/publicado/terminos-admin-v1.0.0.md","artifacts/L-01-superadmin-titular-2026-10-17/ruta-fija-recovery-cli.jar")){
            Path p=root.resolve(relative);if(!Files.isRegularFile(p))throw refused("REQUIRED_HISTORICAL_FILE_ABSENT");selected.add(p);
        }
        selected=selected.stream().distinct().sorted(Comparator.comparing(p->root.relativize(p).toString())).toList();
        for(Path file:selected){String relative=root.relativize(file).toString().replace('\\','/');
            byte[] digest;try(InputStream input=Files.newInputStream(file)){MessageDigest one=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[64*1024];int n;while((n=input.read(buffer))!=-1)one.update(buffer,0,n);digest=one.digest();}
            String expected=switch(relative){
                case "backups/L-00-2026-10-13-final3/source-snapshot.zip" -> "3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b";
                case "docs/legal/publicado/terminos-admin-v1.0.0.md" -> TERMS_SHA256;
                case "artifacts/L-01-superadmin-titular-2026-10-17/ruta-fija-recovery-cli.jar" -> "91913ff7c11cabfaa01881fcbfb24bbf5ed347564c0140d73a757bc96d38e24a";
                default -> null;
            };
            if(expected!=null&&!expected.equals(HexFormat.of().formatHex(digest)))throw refused("PROTECTED_HISTORICAL_FILE_HASH_MISMATCH");
            all.update(relative.getBytes(StandardCharsets.UTF_8));all.update((byte)0);all.update(digest);all.update((byte)'\n');
        }
        return Map.of("fileCount",selected.size(),"sha256",HexFormat.of().formatHex(all.digest()));
    }
    private static void assertHistoricalFilesUnchanged(Map<String,Object> before,Map<String,Object> after){if(!before.equals(after))throw refused("HISTORICAL_EVIDENCE_FILES_CHANGED");}
    private static String sqlState(Throwable failure){for(Throwable x=failure;x!=null;x=x.getCause())if(x instanceof SQLException s&&s.getSQLState()!=null&&s.getSQLState().matches("[A-Z0-9]{5}"))return s.getSQLState();return "NONE";}
    private static Map<String,Object> functionLocation(Throwable failure){
        for(Throwable x=failure;x!=null;x=x.getCause())if(x instanceof org.postgresql.util.PSQLException p&&p.getServerErrorMessage()!=null){
            var out=new LinkedHashMap<String,Object>();int position=p.getServerErrorMessage().getPosition();if(position>0)out.put("sqlSourcePosition",position);
            String where=Objects.toString(p.getServerErrorMessage().getWhere(),"");
            var m=Pattern.compile("PL/pgSQL function ([a-z][a-z0-9_]*)\\([^\\n]*?\\) line ([0-9]+)",Pattern.CASE_INSENSITIVE).matcher(where);
            if(m.find()){out.put("functionName",m.group(1));out.put("functionLine",Integer.parseInt(m.group(2)));}return out;
        }return Map.of();
    }
    private static void silenceLogging(){if(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context)context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);}
    private static Refused refused(String code){return new Refused(code);}
    private static final class Refused extends RuntimeException{
        @java.io.Serial
        private static final long serialVersionUID = 8573144875016366970L;
        private final String code;Refused(String code){super(code);this.code=code;}}
    private static final class CapturedProperties implements DynamicPropertyRegistry{
        private final Map<String,Supplier<Object>> values=new HashMap<>();
        @Override public void add(String name,Supplier<Object> value){values.put(name,value);}
        String value(String key){Supplier<Object> supplier=values.get(key);if(supplier==null)throw refused("VALIDATED_PRIVATE_PROPERTY_UNAVAILABLE");return Objects.toString(supplier.get(),"");}
    }
}
