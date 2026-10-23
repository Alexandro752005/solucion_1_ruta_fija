package pe.rutafija.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only source backup. No restore, migration, schema changes or plaintext dump file. */
public final class TestDatabaseBackupCli {
    private TestDatabaseBackupCli() { }
    public static void main(String[] args) {
        System.exit(run(args));
    }
    static int run(String[] args) {
        int status=1;
        String phase="ARGUMENTS";
        byte[] key=null;Process dump=null;Connection protection=null;ExecutorService workers=Executors.newFixedThreadPool(2);
        Map<String,Object> result=new LinkedHashMap<>();Instant start=Instant.now();
        try {
            if(args.length!=2||!"LOCAL_TEST_READ_ONLY".equals(System.getenv("RF_L08_BACKUP_AUTHORIZATION")))throw new IllegalStateException();
            String url=System.getenv("RF_TEST_DATASOURCE_URL");
            if(url==null||!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):5432/ruta_fija_test(\\?.*)?")
                    ||!"rf_test".equals(System.getenv("RF_TEST_DATASOURCE_USERNAME"))
                    ||!"rf_migrator".equals(System.getenv("SPRING_FLYWAY_USER")))throw new IllegalStateException();
            Path binary=Path.of(args[0]).toRealPath(),output=Path.of(args[1]).toAbsolutePath().normalize();
            if(!binary.getFileName().toString().equalsIgnoreCase("pg_dump.exe")||!Files.isRegularFile(binary)
                    ||Files.exists(output)||!Files.isDirectory(output.getParent())||Files.isSymbolicLink(output.getParent()))throw new IllegalStateException();
            key=Base64.getDecoder().decode(Objects.requireNonNull(System.getenv("RF_L08_BACKUP_KEY_BASE64")));
            if(key.length!=32)throw new IllegalStateException();
            Properties properties=new Properties();properties.setProperty("user","rf_migrator");
            properties.setProperty("password",Objects.requireNonNull(System.getenv("SPRING_FLYWAY_PASSWORD")));
            properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","5");
            phase="SOURCE_CONNECTION";protection=DriverManager.getConnection(url,properties);
            protection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);protection.setReadOnly(true);protection.setAutoCommit(false);
            String snapshot;
            phase="SOURCE_PROTECTION_LOCK";try(var guard=protection.createStatement();var rows=guard.executeQuery("select pg_try_advisory_lock(7518440514)")){
                if(!rows.next()||!rows.getBoolean(1))throw new IllegalStateException();
            }
            for(String folder:java.util.List.of(".local","backend/.local")){
                Path local=Path.of(folder);if(Files.isDirectory(local))try(var entries=Files.list(local)){
                    if(entries.anyMatch(path->path.getFileName().toString().endsWith(".active")))throw new IllegalStateException();
                }
            }
            phase="CONSISTENT_SNAPSHOT";try(var guard=protection.createStatement();var rows=guard.executeQuery("select pg_export_snapshot()")){
                if(!rows.next())throw new IllegalStateException();snapshot=rows.getString(1);
            }
            phase="SOURCE_DATABASE_IDENTITY";{
                Connection connection=protection;
                try(var statement=connection.createStatement();var row=statement.executeQuery("select current_database(),current_user,(select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1),(select count(*) from flyway_schema_history where not success)")){
                    if(!row.next()||!"ruta_fija_test".equals(row.getString(1))||!"rf_migrator".equals(row.getString(2))||row.getInt(4)!=0)throw new IllegalStateException();
                    result.put("sourceSchemaVersion",row.getString(3));
                }
            }
            phase="CONTENT_SCHEMA_FINGERPRINT";result.put("sourceIntegrity",DatabaseIntegrity.capture(protection));
            phase="ACL_ROLE_INVENTORY";
            result.put("referencedAclRoles",DatabaseIntegrity.referencedAclRoles(protection));
            phase="POSTGRES_DUMP_STREAM";ProcessBuilder builder=new ProcessBuilder(binary.toString(),"--host=127.0.0.1","--port=5432","--username=rf_migrator",
                    "--dbname=ruta_fija_test","--format=custom","--no-password","--lock-wait-timeout=5s","--snapshot="+snapshot);
            Map<String,String> environment=builder.environment();
            String systemRoot=System.getenv("SystemRoot");environment.clear();
            if(systemRoot!=null)environment.put("SystemRoot",systemRoot);
            environment.put("PGPASSWORD",properties.getProperty("password"));
            dump=builder.start();final Process owned=dump;final byte[] secretKey=key;
            Future<?> stderr=workers.submit(()->{try(var error=owned.getErrorStream()){error.transferTo(java.io.OutputStream.nullOutputStream());}catch(Exception ignored){}});
            Future<?> encrypted=workers.submit(()->{try(var input=owned.getInputStream();var file=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)){
                new AuthenticatedBackupArchive().encrypt(input,file,secretKey);
            }catch(Exception error){throw new IllegalStateException("BACKUP_STREAM_FAILED");}});
            encrypted.get(30,TimeUnit.MINUTES);
            if(!dump.waitFor(30,TimeUnit.SECONDS)||dump.exitValue()!=0)throw new IllegalStateException();
            stderr.get(5,TimeUnit.SECONDS);
            long plaintextBytes;try(var input=Files.newInputStream(output)){plaintextBytes=new AuthenticatedBackupArchive().verify(input,key,true);}
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(output);var digested=new java.security.DigestInputStream(input,digest)){digested.transferTo(java.io.OutputStream.nullOutputStream());}
            result.put("result","PASS_ENCRYPTED_READABLE_LOCAL_COPY_ONLY_NOT_RESTORE");
            result.put("sourceDatabase","ruta_fija_test");result.put("sourceRole","rf_migrator");
            result.put("archiveSha256",HexFormat.of().formatHex(digest.digest()));result.put("encryptedBytes",Files.size(output));
            result.put("authenticatedPlaintextBytes",plaintextBytes);result.put("plaintextDumpWritten",false);
            result.put("restorationExecuted",false);result.put("independentBackup",false);result.put("scheduledHourlyBackup",false);
            status=0;
        } catch(Exception failure) {
            result.clear();result.put("result","STOPPED_DETAILS_REDACTED_KEEP_STATE");result.put("failureType",failure.getClass().getSimpleName());result.put("failurePhase",phase);
            if(failure instanceof SQLException sql)result.put("sqlState",sql.getSQLState());
        }
        finally {
            if(dump!=null&&dump.isAlive())dump.destroyForcibly();workers.shutdownNow();
            if(protection!=null)try{protection.rollback();protection.close();}catch(SQLException ignored){}
            if(key!=null)Arrays.fill(key,(byte)0);
            result.put("startedAt",start.toString());result.put("finishedAt",Instant.now().toString());
            try{System.out.println(new ObjectMapper().writeValueAsString(result));}catch(Exception ignored){System.out.println("{\"result\":\"STOPPED_DETAILS_REDACTED\"}");}
        }
        return status;
    }
}
