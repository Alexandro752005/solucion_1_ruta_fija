package pe.rutafija.shared.security;

import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.identity.domain.AppUser;

/** Server-resolved mobile identity. The client never supplies these authorities. */
public record MobileDriverActor(AppUser user, Driver driver) {
}
