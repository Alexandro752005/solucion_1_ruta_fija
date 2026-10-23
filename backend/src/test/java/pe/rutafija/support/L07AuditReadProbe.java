package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Read-only hashes/counts, never audit metadata or credentials. Does not run migrations or cleanup. */
public final class L07AuditReadProbe {
    public static void main(String[] args){try{
        if(args.length!=0)throw new IllegalStateException("NO_ARGUMENTS_ALLOWED");
        if(Files.isDirectory(Path.of(".local")))try(var paths=Files.list(Path.of(".local"))){if(paths.anyMatch(p->p.getFileName().toString().endsWith(".active")))throw new IllegalStateException("CASE_MARKER_PRESENT");}
        var config=ManualL01HumanTrial.configuration();
        try(var c=ManualL01HumanTrial.connect(config)){
            c.setAutoCommit(false);c.setReadOnly(true);c.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            try(var q=c.createStatement();var r=q.executeQuery("select current_database()||'/'||current_user")){if(!r.next()||!"ruta_fija_test/rf_test".equals(r.getString(1)))throw new IllegalStateException("TEST_TARGET_REQUIRED");}
            var hash=MessageDigest.getInstance("SHA-256");int count=0;
            try(var q=c.createStatement();var r=q.executeQuery("select to_jsonb(a)::text from audit_event a order by id")){while(r.next()){hash.update(r.getString(1).getBytes(StandardCharsets.UTF_8));hash.update((byte)'\n');count++;}}
            var result=new LinkedHashMap<String,Object>();result.put("observedAt",Instant.now().toString());result.put("result","PASS_READ_ONLY");result.put("database","ruta_fija_test");result.put("role","rf_test");result.put("auditCount",count);result.put("auditSha256",HexFormat.of().formatHex(hash.digest()));result.put("globalCleanup",false);
            var ledger=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))try(var q=c.prepareStatement("select count(*) from pilot_account_creation where role=?")){q.setString(1,role);try(var r=q.executeQuery()){r.next();ledger.put(role,r.getInt(1));}}
            result.put("ledger",ledger);c.rollback();System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }catch(Exception e){System.out.println("{\"result\":\"REFUSED_DETAILS_REDACTED\"}");System.exit(1);}}
}
