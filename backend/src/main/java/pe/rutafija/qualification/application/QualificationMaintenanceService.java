package pe.rutafija.qualification.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import java.util.*;

/** Expiry never silently extends a period or frees an occupied vehicle. */
@Service
@ConditionalOnProperty(name="app.qualification.maintenance.enabled",havingValue="true",matchIfMissing=true)
public class QualificationMaintenanceService {
    private final JdbcTemplate jdbc;private final DriverRepository drivers;private final OperationalQualificationPolicy policy;
    private final DriverQualificationService service;private final AuditService audit;
    public QualificationMaintenanceService(JdbcTemplate jdbc,DriverRepository drivers,OperationalQualificationPolicy policy,DriverQualificationService service,AuditService audit){this.jdbc=jdbc;this.drivers=drivers;this.policy=policy;this.service=service;this.audit=audit;}
    @Scheduled(fixedDelayString="${app.qualification.validity-sweep:PT1M}")
    @Transactional
    public void expireOperationalEligibility(){
        var ids=jdbc.query("select d.id,d.organization_id from driver d join driver_qualification_control c on c.driver_id=d.id where d.active order by d.organization_id,d.id",(r,n)->new UUID[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class)});
        for(var id:ids){
            jdbc.queryForObject("select id from organization where id=? for no key update",UUID.class,id[1]);
            var driver=drivers.lockByIdAndOrganizationId(id[0],id[1]).orElseThrow();
            if(!policy.eligible(id[0],id[1],false)){
                service.effects(driver,"OPERATIONAL_VALIDITY_LOST");
                audit.recordSystem(driver.getOrganization(),"DRIVER_OPERATIONAL_VALIDITY_CHECK","DRIVER",id[0],Map.of("operationalEnabled",false));
            }
        }
        jdbc.update("""
            update qualification_service_attention a set status='RESOLVED',resolved_at=current_timestamp
            from assignment s where a.assignment_id=s.id and a.kind='SAFE_HANDOVER_REQUIRED' and a.status='OPEN'
            and s.status in('COMPLETED','CANCELLED')
            """);
    }
}
