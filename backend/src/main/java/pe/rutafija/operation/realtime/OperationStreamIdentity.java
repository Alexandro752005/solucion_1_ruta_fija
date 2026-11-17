package pe.rutafija.operation.realtime;

import pe.rutafija.identity.domain.AppUser;

import java.util.UUID;

/** Immutable authorization boundary captured when the authenticated ticket is issued. */
record OperationStreamIdentity(UUID organizationId, UUID userId, long sessionVersion, String email) {
    static OperationStreamIdentity from(AppUser actor) {
        return new OperationStreamIdentity(actor.getOrganizationId(), actor.getId(),
                actor.getSessionVersion(), actor.getEmail());
    }
}
