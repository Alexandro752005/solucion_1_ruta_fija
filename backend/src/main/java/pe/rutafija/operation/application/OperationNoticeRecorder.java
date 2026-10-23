package pe.rutafija.operation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.util.*;

/** Synchronous append in the business transaction. A rollback also removes the notice. */
@Service
public class OperationNoticeRecorder {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final ExternalNoticeChannel channel;
    private static final Set<String> REFERENCE_KEYS=Set.of("assignmentId","serviceId","attentionId","incidentId","driverId","vehicleId","announcementId","handoverId");
    public OperationNoticeRecorder(JdbcTemplate jdbc,ObjectMapper json,ExternalNoticeChannel channel){this.jdbc=jdbc;this.json=json;this.channel=channel;}
    @EventListener public void record(OperationChangedEvent event){
        // Live location is not a durable notice. Never duplicate coordinates, OTPs or free text.
        if(event.event().startsWith("driver.location.")||event.event().equals("vehicle.status.changed"))return;
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("NOTICE_REQUIRES_BUSINESS_TRANSACTION");
        var references=new TreeMap<String,String>();event.data().forEach((k,v)->{if(REFERENCE_KEYS.contains(k)&&uuid(v)!=null)references.put(k,uuid(v).toString());});
        var recipients=new LinkedHashSet<>(jdbc.query("select id from app_user where organization_id=? and role='ADMIN' and active",(r,n)->r.getObject(1,UUID.class),event.organizationId()));
        UUID announcement=uuid(event.data().get("announcementId"));UUID assignment=uuid(event.data().get("assignmentId"));UUID driver=uuid(event.data().get("driverId"));UUID incident=uuid(event.data().get("incidentId"));UUID service=uuid(event.data().get("serviceId"));
        if(event.event().equals("announcement.published")&&announcement!=null){
            recipients.addAll(jdbc.query("select user_id from announcement_recipient where announcement_id=? and organization_id=? and withdrawn_at is null",(r,n)->r.getObject(1,UUID.class),announcement,event.organizationId()));
        }else if(!event.event().equals("announcement.read")){
            if(driver!=null)addDrivers(recipients,event.organizationId(),"d.id=?",driver);
            if(assignment!=null)addDrivers(recipients,event.organizationId(),"d.id in(select driver_id from assignment where id=? and organization_id=d.organization_id)",assignment);
            if(incident!=null)addDrivers(recipients,event.organizationId(),"d.id in(select driver_id from incident where id=? and organization_id=d.organization_id)",incident);
            if(service!=null)addDrivers(recipients,event.organizationId(),"d.id in(select driver_id from assignment where service_id=? and organization_id=d.organization_id)",service);
        }
        boolean critical=incident!=null&&Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from incident where id=? and organization_id=? and severity='CRITICAL')",Boolean.class,incident,event.organizationId()));
        boolean external=critical||Set.of("assignment.pending-response","assignment.cancelled","assignment.updated","driver.suspended","service.handover.prepared","service.handover.assumed","service.updated").contains(event.event());
        String status="NOT_REQUIRED",reason="INBOX_ONLY";
        if(external){
            // A configured adapter is still not an authorized dispatch path in L06.
            // Until provider/privacy/device setup and delivery are accredited, fail closed.
            try{var availability=channel.availability();status="NOT_CONFIGURED";reason=availability.configured()?"DISPATCH_NOT_AUTHORIZED":availability.reason();}
            catch(RuntimeException failure){status="FAILED";reason="CHANNEL_AVAILABILITY_CHECK_FAILED";}
        }
        UUID eventId=UUID.randomUUID();String encoded;
        try{encoded=json.writeValueAsString(references);}catch(Exception failure){throw new IllegalStateException("NOTICE_REFERENCE_ENCODING_FAILED");}
        for(UUID user:recipients)jdbc.update("insert into operation_notice(id,event_id,organization_id,user_id,event_type,references_json,created_at,external_status,external_reason) values(?,?,?,?,?,?::jsonb,?,?,?)",UUID.randomUUID(),eventId,event.organizationId(),user,event.event(),encoded,Timestamp.from(event.occurredAt()),status,reason);
        if(event.event().equals("announcement.published")&&announcement!=null&&Boolean.TRUE.equals(jdbc.queryForObject("select require_read_ack from announcement where id=?",Boolean.class,announcement))){
            boolean pending=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from announcement_recipient where announcement_id=? and withdrawn_at is null)",Boolean.class,announcement));
            if(pending)for(UUID user:jdbc.query("select id from app_user where organization_id=? and role='ADMIN' and active",(r,n)->r.getObject(1,UUID.class),event.organizationId()))
                jdbc.update("insert into operation_notice(id,event_id,organization_id,user_id,event_type,references_json,created_at,external_status,external_reason) values(?,?,?,?,?,?::jsonb,?,'NOT_REQUIRED','INBOX_ONLY')",UUID.randomUUID(),UUID.randomUUID(),event.organizationId(),user,"announcement.read.pending",encoded,Timestamp.from(event.occurredAt()));
        }
    }
    private void addDrivers(Set<UUID> result,UUID org,String condition,UUID id){result.addAll(jdbc.query("select u.id from app_user u join driver d on d.user_id=u.id and d.organization_id=u.organization_id where u.organization_id=? and u.role='CONDUCTOR' and u.active and "+condition,(r,n)->r.getObject(1,UUID.class),org,id));}
    private UUID uuid(Object v){try{return v==null?null:UUID.fromString(v.toString());}catch(IllegalArgumentException failure){return null;}}
}
