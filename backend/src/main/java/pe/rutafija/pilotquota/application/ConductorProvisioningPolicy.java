package pe.rutafija.pilotquota.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.util.UUID;

/** Legacy management cannot grant or replace the identity approved in onboarding. */
@Service
public class ConductorProvisioningPolicy {
    private final JdbcTemplate jdbc;
    private final pe.rutafija.qualification.application.OperationalQualificationPolicy qualification;

    public ConductorProvisioningPolicy(JdbcTemplate jdbc, pe.rutafija.qualification.application.OperationalQualificationPolicy qualification) {
        this.jdbc = jdbc;
        this.qualification = qualification;
    }

    public void rejectLegacyCreation() {
        throw conflict("El alta de CONDUCTOR requiere solicitud y aprobación en el flujo de incorporación");
    }

    public void requireApproved(UUID userId, UUID organizationId, UUID driverId) {
        if (userId == null || organizationId == null) {
            throw conflict("El conductor no tiene una identidad aprobada y provisionada");
        }
        Boolean approved = jdbc.queryForObject("""
                select exists (
                    select 1 from conductor_onboarding_request
                    where organization_id = ? and linked_user_id = ?
                      and status = 'APPROVED' and personal_approved_at is not null
                      and provisioned_at is not null
                      and (?::uuid is null or linked_driver_id = ?::uuid)
                )
                """, Boolean.class, organizationId, userId, driverId, driverId);
        if (!Boolean.TRUE.equals(approved)) {
            throw conflict("El conductor requiere aprobación personal y provisión de su expediente");
        }
    }

    public void rejectIdentityChange() {
        throw conflict("Los datos de identidad del conductor deben subsanarse en su expediente restringido");
    }

    public void requireOperationalEligibility(UUID driverId,UUID organizationId) {
        if(!qualification.eligible(driverId,organizationId,true))throw conflict("El conductor requiere habilitación operativa vigente.");
    }
    public void requireActivation(UUID driverId,UUID organizationId){qualification.requireActivation(driverId,organizationId);}
    public boolean operational(UUID driverId,UUID organizationId){return qualification.eligible(driverId,organizationId,true);}

    private ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
