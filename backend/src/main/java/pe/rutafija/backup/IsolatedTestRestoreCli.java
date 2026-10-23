package pe.rutafija.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Restore only a new guarded local test cluster on 55438. Shared test5432 is read-only. */
public final class IsolatedTestRestoreCli {
    private IsolatedTestRestoreCli() { }
    public static void main(String[] args){
        System.exit(run(args));
    }
    static int run(String[] args){
        int status=1;
        byte[] key=null,plaintext=null;Process restore=null;
        var workers=Executors.newFixedThreadPool(2);var result=new LinkedHashMap<String,Object>();
        Instant started=Instant.now();String phase="ARGUMENTS";Path verifiedMarker=null;
        try{
            boolean inspect=args.length==4&&"--inspect-existing-read-only".equals(args[3]);
            boolean locale="NEW_LOCALE_MATCHED_DATABASE_55438".equals(System.getenv("RF_L08_RESTORE_AUTHORIZATION"));
            if((args.length!=3&&!inspect)||(!locale&&!"NEW_EMPTY_CLUSTER_55438".equals(System.getenv("RF_L08_RESTORE_AUTHORIZATION"))))throw new IllegalStateException();
            String expectedDatabase=locale?"ruta_fija_test_l08_locale":"ruta_fija_test";
            Path binary=Path.of(args[0]).toRealPath(),archive=Path.of(args[1]).toRealPath(),marker=Path.of(args[2]).toRealPath();
            if(!binary.getFileName().toString().equalsIgnoreCase("pg_restore.exe")||!Files.isRegularFile(archive)
                    ||!Files.isRegularFile(marker)||!marker.getFileName().toString().equals(locale?"l08-locale-isolated-restore.json":"l08-isolated-restore.json"))throw new IllegalStateException();
            var manifest=new ObjectMapper().readTree(Files.readAllBytes(marker));
            if(!manifest.path("purpose").asText().equals(locale?"L08_LOCALE_MATCHED_SYNTHETIC_TEST_RESTORE_ONLY":"L08_SYNTHETIC_TEST_RESTORE_ONLY")
                    ||manifest.path("port").asInt()!=55438||!manifest.path("database").asText().equals(expectedDatabase))throw new IllegalStateException();
            verifiedMarker=marker;phase="EXACT_ISOLATED_CONNECTION_AND_IDENTITY";
            String target=System.getenv("RF_L08_RESTORE_URL");
            if(!("jdbc:postgresql://127.0.0.1:55438/"+expectedDatabase).equals(target))throw new IllegalStateException();
            var auth=new Properties();auth.setProperty("user","rf_migrator");
            auth.setProperty("password",Objects.requireNonNull(System.getenv("RF_L08_RESTORE_PASSWORD")));
            auth.setProperty("connectTimeout","5");auth.setProperty("socketTimeout","20");
            Path expectedData=Path.of(manifest.path("dataDirectory").asText()).toRealPath();
            try(var connection=DriverManager.getConnection(target,auth);var statement=connection.createStatement()){
                try(var rows=statement.executeQuery("select current_database(),current_user,current_setting('port'),current_setting('data_directory')")){
                    if(!rows.next()||!expectedDatabase.equals(rows.getString(1))||!"rf_migrator".equals(rows.getString(2))
                            ||!"55438".equals(rows.getString(3))||!Path.of(rows.getString(4)).toRealPath().equals(expectedData))throw new IllegalStateException();
                }
                if(locale)try(var rows=statement.executeQuery("select datcollate,datctype from pg_database where datname=current_database()")){
                    if(!rows.next()||!"Spanish_Peru.1252".equals(rows.getString(1))||!"Spanish_Peru.1252".equals(rows.getString(2)))throw new IllegalStateException();
                }
                try(var rows=statement.executeQuery("select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m')")){
                    if(!rows.next())throw new IllegalStateException();
                    int tables=rows.getInt(1);result.put("existingPublicRelations",tables);
                    if(inspect){
                        result.put("result","INSPECTED_EXISTING_ISOLATED_TARGET_READ_ONLY_NO_RESTORE");
                        if(tables>0){var integrity=DatabaseIntegrity.capture(connection);var mapper=new ObjectMapper();var actual=mapper.readTree(mapper.writeValueAsBytes(integrity));var differences=new ArrayList<String>();
                            manifest.path("sourceIntegrity").fieldNames().forEachRemaining(name->{if(!manifest.path("sourceIntegrity").path(name).equals(actual.path(name)))differences.add(name);});
                            result.put("sourceSnapshotMismatchKeys",differences);result.put("integrityMatches",actual.equals(manifest.path("sourceIntegrity")));}
                        status=0;return status;
                    }
                    if(tables!=0)throw new IllegalStateException();
                }
            }
            phase="VERIFY_ARCHIVE_SHA256_AND_GCM";
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(archive);var hashing=new java.security.DigestInputStream(input,digest)){hashing.transferTo(java.io.OutputStream.nullOutputStream());}
            String archiveSha=HexFormat.of().formatHex(digest.digest());
            if(!archiveSha.equals(manifest.path("archiveSha256").asText()))throw new IllegalStateException();
            key=Base64.getDecoder().decode(Objects.requireNonNull(System.getenv("RF_L08_BACKUP_KEY_BASE64")));
            try(var input=Files.newInputStream(archive)){plaintext=new AuthenticatedBackupArchive().authenticatedPostgresBytes(input,key,64*1024*1024);}
            phase="PG_RESTORE_SINGLE_TRANSACTION";
            var command=new ProcessBuilder(binary.toString(),"--host=127.0.0.1","--port=55438","--username=rf_migrator",
                    "--dbname="+expectedDatabase,"--no-password","--exit-on-error","--single-transaction","--no-owner");
            String systemRoot=System.getenv("SystemRoot");command.environment().clear();
            if(systemRoot!=null)command.environment().put("SystemRoot",systemRoot);
            command.environment().put("PGPASSWORD",auth.getProperty("password"));restore=command.start();final Process owned=restore;
            var stdout=workers.submit(()->{try(var stream=owned.getInputStream()){stream.transferTo(java.io.OutputStream.nullOutputStream());}catch(Exception ignored){}});
            var stderr=workers.submit(()->{try(var stream=owned.getErrorStream()){return new String(stream.readNBytes(65536),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ignored){return "";}});
            final byte[] content=plaintext;
            var delivery=CompletableFuture.runAsync(()->{
                try(var input=owned.getOutputStream()){input.write(content);}catch(java.io.IOException failure){throw new CompletionException(failure);}
            });
            delivery.get(15,TimeUnit.MINUTES);
            if(!restore.waitFor(15,TimeUnit.MINUTES))throw new IllegalStateException();
            result.put("pgRestoreExit",restore.exitValue());stdout.get(5,TimeUnit.SECONDS);
            String nativeError=stderr.get(5,TimeUnit.SECONDS);
            if(restore.exitValue()!=0){result.put("nativeErrorClass",nativeError.contains("permission denied")?"PERMISSION_DENIED":nativeError.contains("already exists")?"OBJECT_ALREADY_EXISTS":nativeError.contains("does not exist")?"DEPENDENCY_NOT_FOUND":nativeError.contains("unsupported version")?"UNSUPPORTED_ARCHIVE_VERSION":"PG_RESTORE_FAILED_DETAILS_REDACTED");throw new IllegalStateException();}
            phase="COMPARE_RESTORED_ROWS_SCHEMA_AND_ACL";
            try(var connection=DriverManager.getConnection(target,auth);var statement=connection.createStatement()){
                var integrity=DatabaseIntegrity.capture(connection);
                if(!sameIntegrity(integrity,manifest.path("sourceIntegrity")))throw new IllegalStateException();
                result.put("tableContentAndSchemaIntegrity","PASS_EXACT_SOURCE_SNAPSHOT_HASHES");
                result.put("tablesCompared",integrity.keySet().stream().filter(name->!name.startsWith("schema:")).count());
                try(var rows=statement.executeQuery("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1")){
                    if(!rows.next())throw new IllegalStateException();result.put("restoredSchemaVersion",rows.getString(1));
                }
                try(var rows=statement.executeQuery("select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p')")){
                    rows.next();result.put("restoredPublicTables",rows.getInt(1));if(rows.getInt(1)<1)throw new IllegalStateException();
                }
            }
            result.put("result","PASS_ISOLATED_SQL_RESTORE_ONLY_INTEGRITY_AND_APP_CHECKS_STILL_REQUIRED");
            result.put("targetPort",55438);result.put("database",expectedDatabase);result.put("archiveSha256",archiveSha);
            result.put("sharedTestModified",false);result.put("developmentOrPilotAccessed",false);
            result.put("independentMediaAccredited",false);result.put("rtoServiceRestorationAccredited",false);
            status=0;
        }catch(Exception failure){result.put("result","STOPPED_RESTORE_DETAILS_REDACTED_KEEP_CLUSTER_AND_CASE");result.put("failurePhase",phase);result.put("failureType",failure.getClass().getSimpleName());
            for(Throwable c=failure;c!=null;c=c.getCause())if(c instanceof SQLException sql&&sql.getSQLState()!=null&&sql.getSQLState().matches("[A-Z0-9]{5}")){result.put("sqlState",sql.getSQLState());break;}}
        finally{
            if(restore!=null&&restore.isAlive())restore.destroyForcibly();workers.shutdownNow();
            if(key!=null)Arrays.fill(key,(byte)0);if(plaintext!=null)Arrays.fill(plaintext,(byte)0);
            result.put("startedAt",started.toString());result.put("finishedAt",Instant.now().toString());
            try{String safeJson=new ObjectMapper().writeValueAsString(result);if(verifiedMarker!=null)Files.writeString(verifiedMarker.resolveSibling("restore-attempt-"+UUID.randomUUID()+".json"),safeJson,StandardOpenOption.CREATE_NEW);System.out.println(safeJson);}catch(Exception ignored){System.out.println("{\"result\":\"STOPPED_SAFE_FAILURE\"}");}
        }
        return status;
    }
    static boolean sameIntegrity(Map<String,Object> actual,com.fasterxml.jackson.databind.JsonNode expected)throws java.io.IOException{
        var mapper=new ObjectMapper();
        // Parsed archive JSON uses small integer nodes, JDBC counts are Long.
        // Compare canonical JSON values while preserving every key/count/hash.
        return mapper.readTree(mapper.writeValueAsBytes(actual)).equals(expected);
    }
}
