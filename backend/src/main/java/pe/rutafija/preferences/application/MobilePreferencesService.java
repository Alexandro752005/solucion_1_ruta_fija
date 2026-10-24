package pe.rutafija.preferences.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.preferences.api.MobilePreferencesContract.*;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.MobileDriverContextService;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class MobilePreferencesService {
    private final MobileDriverContextService context;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    public MobilePreferencesService(MobileDriverContextService context, JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.context=context; this.jdbc=jdbc; this.audit=audit; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public View read() {
        var actor=context.requireMobileConsultation();
        return own(actor.user().getId(),actor.user().getOrganizationId());
    }

    @Transactional
    public View update(Update request) {
        var actor=context.requireMobileConsultation();
        UUID user=actor.user().getId(), organization=actor.user().getOrganizationId();
        // A missing preference has version zero. Concurrent first writes compete
        // on the same primary key and then the same version predicate.
        jdbc.update("insert into mobile_preferences(user_id,organization_id,version,optional_notice_alerts,text_size,updated_at) values (?,?,0,true,'NORMAL',?) on conflict(user_id) do nothing",
                user,organization,java.sql.Timestamp.from(Instant.now(clock)));
        View before=own(user,organization);
        int count=jdbc.update("update mobile_preferences set optional_notice_alerts=?,text_size=?,version=version+1,updated_at=? where user_id=? and organization_id=? and version=?",
                request.optionalNoticeAlerts(),request.textSize().name(),java.sql.Timestamp.from(Instant.now(clock)),user,organization,request.version());
        if(count!=1) throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_VERSION_CONFLICT,
                "Las preferencias cambiaron; consulte el estado antes de volver a guardar.");
        View after=own(user,organization);
        audit.record(actor.user(),"MOBILE_PREFERENCES_CHANGED","APP_USER",user,Map.of(
                "changes",java.util.List.of(
                    Map.of("field","optionalNoticeAlerts","before",before.optionalNoticeAlerts(),"after",after.optionalNoticeAlerts()),
                    Map.of("field","textSize","before",before.textSize().name(),"after",after.textSize().name())),
                "version",after.version()));
        return after;
    }

    private View own(UUID user,UUID organization) {
        var rows=jdbc.query("select version,optional_notice_alerts,text_size from mobile_preferences where user_id=? and organization_id=?",
                (rs,n)->new View(rs.getLong(1),rs.getBoolean(2),TextSize.valueOf(rs.getString(3)),true),user,organization);
        return rows.isEmpty()?new View(0,true,TextSize.NORMAL,true):rows.getFirst();
    }
}
