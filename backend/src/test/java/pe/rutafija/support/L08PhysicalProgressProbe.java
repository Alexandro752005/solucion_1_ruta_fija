package pe.rutafija.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
/** Exact live synthetic-case SELECT-only observation; never exports coordinates or row bodies. */
public final class L08PhysicalProgressProbe {
 public static void main(String[] args){String phase="EXACT_CASE";try{
  if(args.length!=0)throw new IllegalStateException();var json=new ObjectMapper();
  Path root=L08AvailabilityRehearsal.repositoryRoot(),local=root.resolve("backend/.local");
  var state=json.readTree(Files.readAllBytes(local.resolve("l08-fis02-state.json")));String run="3f0ba758-6eb9-4d2f-b948-dd1c7de3c430";
  if(!run.equals(state.path("runId").asText())||!run.equals(Files.readString(local.resolve("l08-connected.active")).strip()))throw new IllegalStateException();
  var config=new HashMap<String,Object>();NativePostgresTestDatabase.load().registerSpringProperties((k,v)->config.put(k,v.get()));
  if(!"jdbc:postgresql://127.0.0.1:5432/ruta_fija_test".equals(config.get("spring.datasource.url")))throw new IllegalStateException();
  var properties=new Properties();properties.setProperty("user","rf_test");properties.setProperty("password",config.get("spring.datasource.password").toString());properties.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=15000");config.clear();
  var result=new LinkedHashMap<String,Object>();result.put("runId",run);result.put("observedAt",Instant.now().toString());
  try(var c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/ruta_fija_test",properties)){
   c.setReadOnly(true);phase="TARGET_AND_PROTECTION";
   try(var q=c.createStatement();var r=q.executeQuery("select current_database(),current_user,inet_server_port(),current_setting('transaction_read_only'),pg_try_advisory_lock(7518440514)")){
    if(!r.next()||!"ruta_fija_test".equals(r.getString(1))||!"rf_test".equals(r.getString(2))||r.getInt(3)!=5432||!"on".equals(r.getString(4))||r.getBoolean(5))throw new IllegalStateException();}
   var ledger=new TreeMap<String,Integer>();try(var q=c.createStatement();var r=q.executeQuery("select role,count(*) from pilot_account_creation group by role")){while(r.next())ledger.put(r.getString(1),r.getInt(2));}if(!ledger.equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",8)))throw new IllegalStateException();result.put("historicalLedger",ledger);
   UUID driver=UUID.fromString(state.path("driverId").asText()),account=UUID.fromString(state.path("accountId").asText()),org=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
   try(var q=c.prepareStatement("select u.active,d.active,d.location_consent from app_user u join driver d on d.user_id=u.id where u.id=? and d.id=? and u.organization_id=? and d.organization_id=u.organization_id and u.role='CONDUCTOR'")){
    q.setObject(1,account);q.setObject(2,driver);q.setObject(3,org);try(var r=q.executeQuery()){if(!r.next())throw new IllegalStateException();result.put("accountActive",r.getBoolean(1));result.put("driverActive",r.getBoolean(2));result.put("locationConsent",r.getBoolean(3));}}
   if(!state.path("offerId").asText().isBlank()){
    phase="SERVICE_OFFER_AND_POINTS";UUID offer=UUID.fromString(state.path("offerId").asText()),service=UUID.fromString(state.path("serviceId").asText());
    try(var q=c.prepareStatement("select a.status,s.status from assignment a join scheduled_service s on s.id=a.service_id where a.id=? and s.id=? and a.driver_id=? and a.organization_id=? and s.organization_id=a.organization_id")){
     q.setObject(1,offer);q.setObject(2,service);q.setObject(3,driver);q.setObject(4,org);try(var r=q.executeQuery()){if(!r.next())throw new IllegalStateException();result.put("offerStatus",r.getString(1));result.put("serviceStatus",r.getString(2));}}
    try(var q=c.prepareStatement("select count(*),min(p.received_at),max(p.received_at) from service_gps_point p join service_execution_segment s on s.id=p.segment_id where s.service_id=? and p.driver_id=? and p.organization_id=?")){
     q.setObject(1,service);q.setObject(2,driver);q.setObject(3,org);try(var r=q.executeQuery()){r.next();result.put("gpsPointCount",r.getLong(1));result.put("firstGpsReceivedAt",r.getTimestamp(2)==null?null:r.getTimestamp(2).toInstant().toString());result.put("lastGpsReceivedAt",r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant().toString());}}
    try(var q=c.prepareStatement("select count(*) from service_execution_segment where service_id=? and driver_id=? and ended_at is null")){q.setObject(1,service);q.setObject(2,driver);try(var r=q.executeQuery()){r.next();result.put("openSegments",r.getInt(1));}}
   }
   try(var q=c.createStatement();var r=q.executeQuery("select count(*) from admin_terms_acceptance")){r.next();if(r.getInt(1)!=2)throw new IllegalStateException();result.put("termsAcceptances",2);}
   result.put("markerAndLockActive",true);result.put("databaseWrites",0);result.put("coordinatesOrSecretsRecorded",false);result.put("TEST_CLEANUP_RELEASED",false);result.put("result","PASS_EXACT_LIVE_CASE_READ_ONLY_OBSERVATION");
  }
  Path file=root.resolve("docs/evidencia/L-08/incremento-3-escritorio/fis02-"+run+"/progreso-"+UUID.randomUUID()+".json");Files.write(file,json.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),StandardOpenOption.CREATE_NEW);
  System.out.println(json.writeValueAsString(result));
 }catch(Exception e){System.out.println("L08_FIS02_PROGRESS=STOPPED_KEEP_CASE; PHASE="+phase+"; TYPE="+e.getClass().getSimpleName());System.exit(1);}}
}
