package pe.rutafija.dashboard.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.dashboard.api.DashboardContract;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Read-only priority counts; operational details remain behind their normal tenant endpoints. */
@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService current;
    private final Clock clock;
    public DashboardService(JdbcTemplate jdbc, CurrentUserService current, Clock clock) {
        this.jdbc = jdbc; this.current = current; this.clock = clock;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardContract.Admin admin() {
        var actor = current.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN) throw new ApplicationException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN_ROLE, "Se requiere ADMIN");
        var org = actor.getOrganizationId(); var now = clock.instant(); var until = now.plus(Duration.ofHours(24));
        long critical = count("select count(*) from incident where organization_id=? and severity='CRITICAL' and status in('OPEN','FOLLOW_UP')", org);
        long attention = count("""
            select count(*) from service_attention h where h.organization_id=? and
            (not exists(select 1 from service_attention_release r where r.attention_id=h.id and r.resource_kind='DRIVER')
             or not exists(select 1 from service_attention_release r where r.attention_id=h.id and r.resource_kind='VEHICLE' and r.disposition='READY'))
            """, org);
        long upcoming = count("select count(*) from scheduled_service s left join assignment a on a.id=s.current_offer_id where s.organization_id=? and s.status='PENDING_RESPONSE' and s.scheduled_at<=? and (a.response_deadline_at is null or a.response_deadline_at>?)", org, Timestamp.from(until),Timestamp.from(now));
        long reassign = count("select count(*) from scheduled_service where organization_id=? and status='PENDING_REASSIGNMENT'", org);
        long expiry = count("select count(*) from assignment where organization_id=? and status='PENDING_RESPONSE' and response_deadline_at>? and response_deadline_at<=?", org, Timestamp.from(now), Timestamp.from(until));
        long expired = count("select count(*) from assignment where organization_id=? and status='PENDING_RESPONSE' and response_deadline_at<=?", org, Timestamp.from(now));
        reassign+=expired;
        long incidents = count("select count(*) from incident where organization_id=? and status in('OPEN','FOLLOW_UP')", org);
        long onboarding = count("select count(*) from conductor_onboarding_request where organization_id=? and status='PENDING_REVIEW'", org);
        return new DashboardContract.Admin(now, until, List.of(
            new DashboardContract.Priority(1,"CRITICAL_INCIDENTS",critical,"/incidents"),
            new DashboardContract.Priority(2,"SAFETY_ATTENTION",attention,"/assignments"),
            new DashboardContract.Priority(3,"UPCOMING_UNACCEPTED",upcoming,"/assignments"),
            new DashboardContract.Priority(4,"PENDING_REASSIGNMENT",reassign,"/assignments"),
            new DashboardContract.Priority(5,"EXPIRED_PENDING_OFFERS",expired,"/assignments"),
            new DashboardContract.Priority(6,"EXPIRING_OFFERS",expiry,"/assignments"),
            new DashboardContract.Priority(7,"OPEN_INCIDENTS",incidents,"/incidents"),
            new DashboardContract.Priority(8,"ONBOARDING_REVIEW",onboarding,"/onboarding-requests")),
            count("select count(*) from scheduled_service where organization_id=? and status='IN_SERVICE'",org),
            count("select count(*) from driver where organization_id=? and active",org),
            count("select count(*) from vehicle where organization_id=? and active",org));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardContract.Platform platform() {
        current.requireSuperAdminActor();
        return new DashboardContract.Platform(clock.instant(),
            count("select count(*) from organization"), count("select count(*) from organization where status='ACTIVE'"),
            count("select count(*) from app_user where role='ADMIN' and active"),
            count("select count(*) from admin_activation a join app_user u on u.id=a.admin_user_id where not u.active and a.status in('PENDING_CLAIM','IN_PROGRESS')"));
    }
    private long count(String sql, Object... args) { var result=jdbc.queryForObject(sql,Long.class,args); return result==null?0L:result; }
}
