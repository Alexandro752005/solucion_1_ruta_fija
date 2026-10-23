package pe.rutafija.operation.infrastructure;
import org.springframework.stereotype.Component;
import pe.rutafija.operation.application.ExternalNoticeChannel;
/** Deliberately no SDK, credentials, device registration, network call or retry. */
@Component
public class UnconfiguredExternalNoticeChannel implements ExternalNoticeChannel {
    @Override public Availability availability(){return new Availability(false,"PROVIDER_AND_DEVICE_NOT_PREPARED");}
}
