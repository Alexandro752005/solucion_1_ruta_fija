package pe.rutafija.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
public final class L08DatabaseLocaleProbe {
 public static void main(String[] args){try{
  var cfg=new LinkedHashMap<String,Object>();NativePostgresTestDatabase.load().registerSpringProperties((k,v)->cfg.put(k,v.get()));
  var evidence=new LinkedHashMap<String,Object>();
  try(var c=DriverManager.getConnection(cfg.get("RF_TEST_MIGRATOR_URL").toString(),cfg.get("RF_TEST_MIGRATOR_USERNAME").toString(),cfg.get("RF_TEST_MIGRATOR_PASSWORD").toString())){c.setReadOnly(true);evidence.put("source",locale(c,5432));}
  if(!"jdbc:postgresql://127.0.0.1:55438/ruta_fija_test".equals(System.getenv("RF_L08_RESTORE_URL")))throw new IllegalStateException();
  try(var c=DriverManager.getConnection(System.getenv("RF_L08_RESTORE_URL"),"rf_migrator",System.getenv("RF_L08_RESTORE_PASSWORD"))){c.setReadOnly(true);evidence.put("isolatedTarget",locale(c,55438));}
  evidence.put("databaseWrites",0);System.out.println(new ObjectMapper().writeValueAsString(evidence));
 }catch(Exception failure){System.out.println("L08_LOCALE_PROBE=STOPPED_DETAILS_REDACTED");System.exit(1);}}
 static Map<String,String> locale(Connection c,int port)throws SQLException{
  try(var q=c.createStatement();var r=q.executeQuery("select current_database(),current_user,inet_server_port(),pg_encoding_to_char(encoding),datcollate,datctype,datlocprovider from pg_database where datname=current_database()")){
   if(!r.next()||!r.getString(1).equals("ruta_fija_test")||!r.getString(2).equals("rf_migrator")||r.getInt(3)!=port)throw new IllegalStateException();
   return Map.of("database",r.getString(1),"role",r.getString(2),"port",r.getString(3),"encoding",r.getString(4),"collation",r.getString(5),"ctype",r.getString(6),"provider",r.getString(7));
  }
 }
}
