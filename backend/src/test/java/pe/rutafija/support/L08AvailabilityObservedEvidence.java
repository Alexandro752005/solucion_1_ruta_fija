package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Reconciles the already observed outage; never repeats it or rewrites its failed stop result. */
public final class L08AvailabilityObservedEvidence {
    public static void main(String[] args) {
        String phase="ARGUMENTS";
        try {
            if(!Arrays.equals(args,new String[]{"--reconcile-observed-case"}))throw new IllegalStateException();
            Path root=L08AvailabilityRehearsal.repositoryRoot(), directory=L08AvailabilityRehearsal.runDirectory();
            Path original=directory.resolve("result.json"), output=directory.resolve("reconciliation.json");
            if(Files.exists(output))throw new IllegalStateException();
            var json=new ObjectMapper(); var prior=json.readTree(Files.readAllBytes(original));
            if(!L08AvailabilityRehearsal.RUN_ID.equals(prior.path("runId").asText())
                    || !"NORMAL_MONITOR_STOP_AND_ARCHIVE".equals(prior.path("failurePhase").asText())
                    || !"STOPPED_KEEP_CASE_AND_DIAGNOSE_DETAILS_REDACTED".equals(prior.path("result").asText()))throw new IllegalStateException();
            phase="LOCAL_PROCESSES_AND_PORT";
            try(var socket=new ServerSocket()){socket.setReuseAddress(false);socket.bind(new InetSocketAddress("127.0.0.1",4312));}
            if(ProcessHandle.allProcesses().anyMatch(p -> p.info().arguments().map(a -> Arrays.asList(a).contains(L08ReadOnlyHealthServer.class.getName())
                    || Arrays.asList(a).contains("--owned-monitor-child")).orElse(false)))throw new IllegalStateException();
            try(var markers=Files.list(root.resolve("backend/.local"))){if(markers.anyMatch(p -> p.getFileName().toString().endsWith(".active")))throw new IllegalStateException();}
            phase="IMMUTABLE_HTTP_OBSERVATIONS";
            Path monitor;try(var runs=Files.list(directory.resolve("monitor"))){var all=runs.filter(Files::isDirectory).toList();if(all.size()!=1)throw new IllegalStateException();monitor=all.getFirst();}
            Path journal=monitor.resolve("events.jsonl");var records=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            for(String line:Files.readAllLines(journal))records.add(json.readTree(line));
            var events=records.stream().filter(r -> "INCIDENT_EVENT".equals(r.path("type").asText())).toList();
            if(events.size()!=2 || !events.getFirst().equals(prior.path("confirmedFailure")) || !events.getLast().equals(prior.path("verifiedRecovery")))throw new IllegalStateException();
            var samples=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            for(int i=0;i<2;i++){
                final var event=events.get(i);var sample=records.stream().filter(r -> "HTTP_SAMPLE".equals(r.path("type").asText()) && event.path("at").equals(r.path("at"))).findFirst().orElseThrow();
                var probes=new HashMap<String,String>();for(var p:sample.path("details").path("probes"))probes.put(p.path("probeId").asText(),p.path("outcome").asText());
                if(!probes.keySet().equals(Set.of("api-readiness","api-liveness","crm-application")) || !"HEALTHY".equals(probes.get("crm-application")))throw new IllegalStateException();
                for(String id:List.of("api-readiness","api-liveness"))if(i==0?!Set.of("TRANSPORT_ERROR","TIMEOUT").contains(probes.get(id)):!"HEALTHY".equals(probes.get(id)))throw new IllegalStateException();
                samples.add(sample);
            }
            var alerts=records.stream().filter(r -> "LOCAL_TEST_ALERT".equals(r.path("type").asText())).toList();
            if(alerts.size()!=2)throw new IllegalStateException();
            for(var alert:alerts){Path file=monitor.resolve("local-test-alerts").resolve(alert.path("details").path("reference").asText()).normalize();
                if(!file.startsWith(monitor.resolve("local-test-alerts")) || !L08AvailabilityRehearsal.sha256(file).equals(alert.path("details").path("sha256").asText()))throw new IllegalStateException();}
            phase="READ_ONLY_DATABASE_AND_RELEASED_LOCK";
            var config=new LinkedHashMap<String,Object>();var db=NativePostgresTestDatabase.load();db.assertTestRolePermissions();db.registerSpringProperties((k,v)->config.put(k,v.get()));
            var credentials=new Properties();credentials.put("user",config.get("RF_TEST_MIGRATOR_USERNAME"));credentials.put("password",config.get("RF_TEST_MIGRATOR_PASSWORD"));
            credentials.put("connectTimeout","5");credentials.put("socketTimeout","20");var hashes=new TreeMap<String,String>();
            try(var connection=DriverManager.getConnection(config.get("RF_TEST_MIGRATOR_URL").toString(),credentials)){
                connection.setReadOnly(true);
                try(var s=connection.createStatement();var r=s.executeQuery("select current_database(),current_user,pg_try_advisory_lock(7518440514)")){
                    if(!r.next()||!"ruta_fija_test".equals(r.getString(1))||!"rf_migrator".equals(r.getString(2))||!r.getBoolean(3))throw new IllegalStateException();}
                var names=prior.path("before").path("publicTableRowsSha256").fieldNames();
                while(names.hasNext()){String table=names.next();if(!table.matches("[a-z][a-z0-9_]*"))throw new IllegalStateException();
                    hashes.put(table,L07DatabasePreflight.digestRows(connection,"select row_to_json(t)::text from public."+table+" t order by row_to_json(t)::text"));}
                if(!json.valueToTree(hashes).equals(prior.path("before").path("publicTableRowsSha256")))throw new IllegalStateException();
                try(var s=connection.createStatement();var r=s.executeQuery("select pg_advisory_unlock(7518440514)")){if(!r.next()||!r.getBoolean(1))throw new IllegalStateException();}
            }
            phase="ARCHIVE_NEW_RECONCILIATION_WITHOUT_REWRITING_HISTORY";
            var evidence=new LinkedHashMap<String,Object>();
            evidence.put("runId",L08AvailabilityRehearsal.RUN_ID);evidence.put("observedAt",Instant.now().toString());
            evidence.put("originalResultSha256",L08AvailabilityRehearsal.sha256(original));evidence.put("originalResultPreserved",true);
            evidence.put("originalMonitorStop", "FAILED_DURING_INTERRUPTED_JOURNAL_WRITE_NOT_RETROACTIVELY_PASSED");
            evidence.put("monitorJournalSha256",L08AvailabilityRehearsal.sha256(journal));evidence.put("httpSamples",samples);
            evidence.put("stopCommandToConfirmedDetectionMillis",prior.path("stopCommandToConfirmedDetectionMillis").asLong());
            evidence.put("confirmedDetectionToVerifiedHealthRecoveryMillis",prior.path("confirmedDetectionToVerifiedHealthRecoveryMillis").asLong());
            evidence.put("databaseTablesCompared",hashes.size());evidence.put("databaseRowsLedgerAndAcceptancesPreserved",true);
            evidence.put("ownedApiAndMonitorAbsent",true);evidence.put("cleanupProtectionReleased",true);evidence.put("smtpAttempts",0);evidence.put("databaseWrites",0);
            evidence.put("result","PASS_OBSERVED_OUTAGE_RETURN_AND_RESOURCE_RECONCILIATION_ONLY");
            evidence.put("limits",List.of("The original monitor stop failure remains historical","Cooperative stop correction requires its separately executed regression",
                "Same PC and accelerated one-second cadence","Health endpoints only, not business restoration","No independent monitoring, external alerts,24x7 coverage,backup,restore or RPO credit"));
            L08AvailabilityRehearsal.writeNewJson(output,evidence);
            System.out.println("L08_AVAILABILITY_RECONCILIATION="+evidence.get("result"));System.out.println("L08_AVAILABILITY_EVIDENCE_SHA256="+L08AvailabilityRehearsal.sha256(output));
        }catch(Exception failure){System.out.println("L08_AVAILABILITY_RECONCILIATION=STOPPED_KEEP_ORIGINAL_CASE");System.out.println("L08_AVAILABILITY_RECONCILIATION_PHASE="+phase);System.out.println("L08_AVAILABILITY_RECONCILIATION_TYPE="+failure.getClass().getSimpleName());System.exit(1);}
    }
}
