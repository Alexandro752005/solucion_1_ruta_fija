package pe.rutafija.livemap.application;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.livemap.api.LiveMapContract;
import pe.rutafija.livemap.infrastructure.LiveMapReadDao;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
public class LiveMapService {
    private static final Set<Integer> LIMITS=Set.of(5,10,15,20);
    private final CurrentUserService current;
    private final LiveMapReadDao dao;
    private final Clock clock;
    public LiveMapService(CurrentUserService current, LiveMapReadDao dao, Clock clock) {
        this.current=current;this.dao=dao;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public LiveMapContract.Vehicles vehicles(int limit, UUID groupId, String driverName) {
        var actor=admin();
        if(!LIMITS.contains(limit))throw invalid("El limite debe ser 5, 10, 15 o 20.");
        if(driverName!=null&&driverName.length()>100)throw invalid("El nombre admite hasta 100 caracteres.");
        var now=clock.instant();
        var items=dao.vehicles(actor.getOrganizationId(),limit,groupId,driverName==null?"":driverName.strip(),now);
        return new LiveMapContract.Vehicles(now,limit,items);
    }
    @Transactional(readOnly=true)
    public LiveMapContract.Groups groups() {
        return new LiveMapContract.Groups(dao.groups(admin().getOrganizationId()));
    }
    private AppUser admin() {
        var actor=current.requireTenantActor();
        if(actor.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"El mapa de la organizacion requiere ADMIN.");
        return actor;
    }
    private static ApplicationException invalid(String message){return new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,message);}
}
