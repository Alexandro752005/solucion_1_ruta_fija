package pe.rutafija.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public final class L08ConnectedProtectionProbe {
 public static void main(String[] args){try{
  Path root=L08AvailabilityRehearsal.repositoryRoot(),local=root.resolve("backend/.local");var json=new ObjectMapper();
  var state=json.readTree(Files.readAllBytes(local.resolve(L08ConnectedTrial.COMPLEMENT?"l08-fis02-state.json":"l08-connected-state.json")));
  if(!L08ConnectedTrial.RUN.equals(state.path("runId").asText())||!state.path("prepared").asBoolean()||!L08ConnectedTrial.RUN.equals(Files.readString(local.resolve("l08-connected.active")).strip()))throw new IllegalStateException();
  var cfg=new HashMap<String,Object>();NativePostgresTestDatabase.load().registerSpringProperties((k,v)->cfg.put(k,v.get()));
  var result=new LinkedHashMap<String,Object>();result.put("runId",L08ConnectedTrial.RUN);result.put("observedAt",Instant.now().toString());
  try(var c=DriverManager.getConnection(cfg.get("spring.datasource.url").toString(),"rf_test",cfg.get("spring.datasource.password").toString())){
   c.setReadOnly(true);try(var q=c.createStatement();var r=q.executeQuery("select pg_try_advisory_lock(7518440514)")){
    if(!r.next())throw new IllegalStateException();if(r.getBoolean(1)){try(var unlock=c.createStatement()){unlock.execute("select pg_advisory_unlock(7518440514)");}throw new IllegalStateException();}}
   var ledger=new TreeMap<String,Integer>();try(var q=c.createStatement();var r=q.executeQuery("select role,count(*) from pilot_account_creation group by role")){while(r.next())ledger.put(r.getString(1),r.getInt(2));}
   if(!ledger.equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",L08ConnectedTrial.EXPECTED_CONDUCTOR_AFTER)))throw new IllegalStateException();result.put("historicalLedger",ledger);
   try(var q=c.prepareStatement("select u.active,u.email_verified_at is not null,d.active,d.location_consent from app_user u join driver d on d.user_id=u.id where u.id=? and d.id=? and u.organization_id=? and d.organization_id=u.organization_id and u.role='CONDUCTOR'")){
    q.setObject(1,UUID.fromString(state.path("accountId").asText()));q.setObject(2,UUID.fromString(state.path("driverId").asText()));q.setObject(3,L08ConnectedTrial.ORG);
    try(var row=q.executeQuery()){if(!row.next()||!row.getBoolean(1)||!row.getBoolean(2)||!row.getBoolean(3))throw new IllegalStateException();result.put("currentLocationConsent",row.getBoolean(4));}
   }
   try(var q=c.createStatement();var row=q.executeQuery("select count(*) from admin_terms_acceptance")){if(!row.next()||row.getInt(1)!=2)throw new IllegalStateException();result.put("termsAcceptancesPreserved",2);}
  }
  result.put("markerMatches",true);result.put("cleanupLockHeldByCase",true);result.put("rollbackGuard",state.path("rollbackFixtureGuard").asText());result.put("TEST_CLEANUP_RELEASED",false);result.put("result","PASS_PROTECTION_AND_ONE_NORMAL_SYNTHETIC_CREATION_ONLY");
  Path file=root.resolve("docs/evidencia/L-08/conectado-"+L08ConnectedTrial.RUN+"/proteccion-"+UUID.randomUUID()+".json");L08AvailabilityRehearsal.writeNewJson(file,result);
  System.out.println("L08_CONNECTED_PROTECTION="+result.get("result"));System.out.println("L08_CONNECTED_LEDGER=1/3/"+L08ConnectedTrial.EXPECTED_CONDUCTOR_AFTER);
 }catch(Exception failure){System.out.println("L08_CONNECTED_PROTECTION=STOPPED_KEEP_CASE_AND_MARKER");System.exit(1);}}
}
