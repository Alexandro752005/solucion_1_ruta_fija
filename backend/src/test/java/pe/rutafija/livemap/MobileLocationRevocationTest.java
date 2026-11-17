package pe.rutafija.livemap;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.DriverVehicleLinkRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.api.dto.mobile.MobileLocationConsentRequest;
import pe.rutafija.operation.application.*;
import pe.rutafija.operation.config.MobileOperationProperties;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.DriverCurrentLocationStore;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.qualification.application.OperationalQualificationPolicy;
import pe.rutafija.shared.security.MobileDriverActor;
import pe.rutafija.shared.security.MobileDriverContextService;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class MobileLocationRevocationTest {
    @Test void revocationDeletesTheSnapshotAndPublishesOnlyAnInvalidation() {
        var org=Organization.active("Synthetic revoke",null,"America/Lima");
        var user=AppUser.organizationUser(org,"revoke@example.invalid","synthetic-hash","Synthetic driver",UserRole.CONDUCTOR);
        var group=TransportGroup.active(org,"Synthetic group",null);
        var driver=Driver.register(org,user,group,"Synthetic driver",null,"DNI","77777777",null);
        var actor=new MobileDriverActor(user,driver);
        var context=mock(MobileDriverContextService.class);var drivers=mock(DriverRepository.class);
        var locations=mock(DriverCurrentLocationStore.class);var events=mock(OperationEventPublisher.class);
        when(context.requireMobileConsultation()).thenReturn(actor);
        when(drivers.lockByIdAndOrganizationId(driver.getId(),org.getId())).thenReturn(Optional.of(driver));
        var service=new MobileDriverService(context,drivers,mock(DriverVehicleLinkRepository.class),locations,
                new MobileOperationProperties(Duration.ofMinutes(5),Duration.ofMinutes(5),Duration.ofSeconds(30)),
                mock(MobileCommandProcessor.class),mock(AuditService.class),events,Clock.systemUTC(),
                mock(OrganizationSchedulingLock.class),mock(AssignmentRepository.class),mock(OperationalQualificationPolicy.class),
                mock(EntityManager.class),mock(ServiceExecution.class));
        service.changeLocationConsent(new MobileLocationConsentRequest(false));
        var order=inOrder(locations,events);
        order.verify(locations).deleteByDriverId(driver.getId());
        order.verify(events).publish(org.getId(),group.getId(),"driver.location.cleared",Map.of("driverId",driver.getId().toString()));
        verifyNoMoreInteractions(events);
    }
}
