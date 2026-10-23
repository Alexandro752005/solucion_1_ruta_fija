package pe.rutafija.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
/** Static schema metadata only: no table contents, function bodies or credentials. */
public final class L08RestoreSchemaDifferences {
 public static void main(String[] args){try{
  var config=new HashMap<String,Object>();NativePostgresTestDatabase.load().registerSpringProperties((k,v)->config.put(k,v.get()));
  if(!"jdbc:postgresql://127.0.0.1:55438/ruta_fija_test_l08_locale".equals(System.getenv("RF_L08_RESTORE_URL")))throw new IllegalStateException();
  try(var source=DriverManager.getConnection(config.get("RF_TEST_MIGRATOR_URL").toString(),"rf_migrator",config.get("RF_TEST_MIGRATOR_PASSWORD").toString());var target=DriverManager.getConnection(System.getenv("RF_L08_RESTORE_URL"),"rf_migrator",System.getenv("RF_L08_RESTORE_PASSWORD"))){
   source.setReadOnly(true);target.setReadOnly(true);var result=new LinkedHashMap<String,Object>();
   for(String type:List.of("constraints","indexes","acl")){var a=definitions(source,type);var b=definitions(target,type);var differences=new ArrayList<Map<String,Object>>();
    for(String key:b.keySet())if(!Objects.equals(a.get(key),b.get(key)))differences.add(Map.of("schemaObject",key,"source",a.getOrDefault(key,"ABSENT"),"restored",b.get(key)));
    var sourceOnly=new TreeSet<>(a.keySet());sourceOnly.removeAll(b.keySet());
    result.put(type,Map.of("sourceObjectCount",a.size(),"targetObjectCount",b.size(),"commonTargetDifferences",differences,"sourceOnlyKeys",sourceOnly));
   }result.put("sourceVersion","50");result.put("restoredVersion","48");result.put("limits","Only static schema; V49/V50 differences expected and named separately. Never compare current source table contents against the earlier snapshot.");
   System.out.println(new ObjectMapper().writeValueAsString(result));
  }
 }catch(Exception failure){System.out.println("L08_RESTORE_SCHEMA_DIFFERENCES=STOPPED_DETAILS_REDACTED");System.exit(1);}}
 static Map<String,String> definitions(Connection c,String type)throws SQLException{
  String sql=switch(type){
   case "constraints"->"select c.relname||'.'||p.conname,pg_get_constraintdef(p.oid,true) from pg_constraint p join pg_class c on c.oid=p.conrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public'";
   case "indexes"->"select indexname,indexdef from pg_indexes where schemaname='public'";
   default->"select object||'|'||coalesce(r.rolname,'PUBLIC')||'|'||privilege_type,is_grantable::text from(select 'table:'||c.relname object,a.grantee,a.privilege_type,a.is_grantable from pg_class c join pg_namespace n on n.oid=c.relnamespace cross join lateral aclexplode(c.relacl)a where n.nspname='public' union all select 'function:'||p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',a.grantee,a.privilege_type,a.is_grantable from pg_proc p join pg_namespace n on n.oid=p.pronamespace cross join lateral aclexplode(p.proacl)a where n.nspname='public') x left join pg_roles r on r.oid=x.grantee";
  };
  var result=new TreeMap<String,String>();try(var q=c.createStatement();var rows=q.executeQuery(sql)){while(rows.next())result.put(rows.getString(1),rows.getString(2));}return result;
 }
}
