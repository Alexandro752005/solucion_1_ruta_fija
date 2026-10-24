package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import pe.rutafija.backup.DatabaseIntegrity;
import pe.rutafija.backup.SqlLiteralArrayCast;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Full original V50 snapshot comparison; only proven reversible SQL spellings/owner defaults. */
public final class L08Rest03Equivalence {
    private static final ObjectMapper JSON=new ObjectMapper();
    public static void main(String[] args){String phase="EXACT_CASE";try{
        if(args.length!=1||!"REST03_V50_55440_READ_ONLY".equals(System.getenv("RF_L08_REST03_PROOF_AUTHORIZATION")))throw new IllegalStateException();
        Path manifestFile=Path.of(args[0]).toRealPath();var manifest=JSON.readTree(Files.readAllBytes(manifestFile));
        if(!"8ba8e8bc-ea46-40bd-9848-11a11a79b9fd".equals(manifest.path("caseId").asText())||manifest.path("port").asInt()!=55440||!"50".equals(manifest.path("sourceSchemaVersion").asText()))throw new IllegalStateException();
        var out=new LinkedHashMap<String,Object>();
        try(var source=connect(5432,System.getenv("RF_L08_REST03_SOURCE_PASSWORD"));var target=connect(55440,System.getenv("RF_L08_REST03_TARGET_PASSWORD"))){
            phase="SOURCE_STILL_EQUALS_EXACT_ORIGINAL_BACKUP";var expected=DatabaseIntegrity.capture(source);
            if(!tree(expected).equals(manifest.path("sourceIntegrity")))throw new IllegalStateException();
            phase="ALL_RAW_TABLES_AND_SCHEMA";var actual=DatabaseIntegrity.capture(target);var raw=tree(actual);var mismatch=new ArrayList<String>();
            manifest.path("sourceIntegrity").fieldNames().forEachRemaining(n->{if(!manifest.path("sourceIntegrity").path(n).equals(raw.path(n)))mismatch.add(n);});
            if(!Set.of("schema:constraints","schema:indexes","schema:acl").containsAll(mismatch))throw new IllegalStateException();
            var rewrites=new TreeMap<String,Integer>();
            for(String kind:List.of("constraints","indexes")){
                phase="REVERSIBLE_"+kind.toUpperCase(Locale.ROOT);
                String sql=kind.equals("indexes")?"select indexname,indexname||'|'||indexdef from pg_indexes where schemaname='public'":"select n.nspname||'.'||t.relname||'.'||p.conname,n.nspname||'.'||t.relname||'|'||p.conname||'|'||pg_get_constraintdef(p.oid,true) from pg_constraint p join pg_class t on t.oid=p.conrelid join pg_namespace n on n.oid=t.relnamespace where n.nspname='public'";
                var before=definitions(source,sql);var restored=definitions(target,sql);if(!before.keySet().equals(restored.keySet()))throw new IllegalStateException();
                int changed=0;var normalized=new ArrayList<String>();
                for(var entry:restored.entrySet()){
                    String original=before.get(entry.getKey()),value=entry.getValue();
                    if(!original.equals(value)){
                        String reversed=SqlLiteralArrayCast.sourceSpelling(value,kind.equals("indexes"));
                        if(!original.equals(reversed))throw new IllegalStateException();value=reversed;changed++;
                    }
                    normalized.add(value);
                }
                actual.put("schema:"+kind,aggregate(target,normalized,kind.equals("constraints")));rewrites.put(kind,changed);
            }
            phase="OWNER_DEFAULTS_ONLY_WHEN_SOURCE_HAS_ALL_SEVEN_OWNER_RIGHTS";
            var sourceAcl=definitions(source,"select c.relname,coalesce(c.relacl::text,'NULL') from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public'");
            var restoredAcl=definitions(target,"select c.relname,coalesce(c.relacl::text,'NULL') from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public'");
            var expanded=new ArrayList<String>();
            for(String table:sourceAcl.keySet())if(!sourceAcl.get(table).equals(restoredAcl.get(table))){
                if(!Set.of("retention_execution_authority","retention_personal_row_authority").contains(table)||!"NULL".equals(restoredAcl.get(table)))throw new IllegalStateException();
                for(var connection:List.of(source,target))for(String role:List.of("rf_migrator","rf_app","rf_test"))for(String privilege:List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE","REFERENCES","TRIGGER")){
                    try(var q=connection.prepareStatement("select pg_get_userbyid(c.relowner),has_table_privilege(?,c.oid,?) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname=?")){
                        q.setString(1,role);q.setString(2,privilege);q.setString(3,table);try(var r=q.executeQuery()){if(!r.next()||!"rf_migrator".equals(r.getString(1))||r.getBoolean(2)!=role.equals("rf_migrator"))throw new IllegalStateException();}
                    }
                }
                expanded.add(table);
            }
            String names=expanded.stream().map(t->"'"+t+"'").collect(java.util.stream.Collectors.joining(","));if(names.isEmpty())names="''";
            String sql="select identity||'|'||coalesce(grantee.rolname,'PUBLIC')||'|'||privilege_type||'|'||is_grantable from (select 'table:'||t.relname identity,a.grantee,a.privilege_type,a.is_grantable from pg_class t join pg_namespace n on n.oid=t.relnamespace cross join lateral aclexplode(coalesce(t.relacl,case when t.relname in("+names+") then acldefault('r',t.relowner) else null end))a where n.nspname='public' union all select 'function:'||p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',a.grantee,a.privilege_type,a.is_grantable from pg_proc p join pg_namespace n on n.oid=p.pronamespace cross join lateral aclexplode(p.proacl)a where n.nspname='public') permissions left join pg_roles grantee on grantee.oid=permissions.grantee";
            var acl=new ArrayList<String>();try(var q=target.createStatement();var r=q.executeQuery(sql)){while(r.next())acl.add(r.getString(1));}
            actual.put("schema:acl",aggregate(target,acl,false));phase="EVERY_ORIGINAL_HASH_AND_COUNT";
            if(!tree(actual).equals(manifest.path("sourceIntegrity")))throw new IllegalStateException();
            out.put("caseId",manifest.path("caseId").asText());out.put("result","PASS_FULL_V50_SNAPSHOT_EQUIVALENCE_READ_ONLY");out.put("observedAt",Instant.now().toString());
            out.put("rawMismatchCategoriesPreserved",mismatch);out.put("rawTargetIntegrity",raw);out.put("reconciledTargetIntegrity",actual);out.put("reversibleLiteralSpellings",rewrites);out.put("ownerDefaultsExpandedOnlyFor",expanded);
            out.put("allOriginalCountsAndHashesRequired",true);out.put("originalManifestRewritten",false);out.put("databaseWrites",0);out.put("sourcePort",5432);out.put("targetPort",55440);
            Files.write(manifestFile.resolveSibling("equivalence-result.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(out),StandardOpenOption.CREATE_NEW);
            System.out.println("L08_REST03_EQUIVALENCE=PASS_FULL_V50_SNAPSHOT_EQUIVALENCE_READ_ONLY");
        }
    }catch(Exception e){System.out.println("L08_REST03_EQUIVALENCE=STOPPED_KEEP_CASE");System.out.println("PHASE="+phase);System.out.println("TYPE="+e.getClass().getSimpleName());System.exit(1);}}
    private static Connection connect(int port,String password)throws Exception{
        var p=new Properties();p.setProperty("user","rf_migrator");p.setProperty("password",Objects.requireNonNull(password));p.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=20000");
        var c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+port+"/ruta_fija_test",p);c.setReadOnly(true);
        try(var q=c.createStatement();var r=q.executeQuery("select current_database(),current_user,inet_server_port(),datcollate,datctype from pg_database where datname=current_database()")){
            if(!r.next()||!"ruta_fija_test".equals(r.getString(1))||!"rf_migrator".equals(r.getString(2))||r.getInt(3)!=port||!"Spanish_Peru.1252".equals(r.getString(4))||!"Spanish_Peru.1252".equals(r.getString(5)))throw new IllegalStateException();}
        return c;
    }
    private static Map<String,String> definitions(Connection c,String sql)throws Exception{var v=new TreeMap<String,String>();try(var q=c.createStatement();var r=q.executeQuery(sql)){while(r.next()){if(v.put(r.getString(1),r.getString(2))!=null)throw new IllegalStateException();}}return v;}
    private static com.fasterxml.jackson.databind.JsonNode tree(Object v)throws Exception{return JSON.readTree(JSON.writeValueAsBytes(v));}
    private static Map<String,Object> aggregate(Connection c,List<String> values,boolean cOrder)throws Exception{
        try(var q=c.prepareStatement("select count(*),encode(sha256(convert_to(coalesce(string_agg(x,E'\\n' order by x"+(cOrder?" COLLATE \"C\"":"")+"),''),'UTF8')),'hex') from unnest(?::text[])x")){
            var array=c.createArrayOf("text",values.toArray(String[]::new));try{q.setArray(1,array);try(var r=q.executeQuery()){r.next();return Map.of("count",r.getLong(1),"sha256",r.getString(2));}}finally{array.free();}}
    }
}
