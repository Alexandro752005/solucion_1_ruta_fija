package pe.rutafija.operation.realtime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.api.dto.OperationStreamTicketResponse;
import pe.rutafija.operation.config.OperationProperties;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Emite tickets efímeros y de un solo uso; nunca pone el JWT en la URL del socket. */
@Service
public class OperationStreamTicketService {

    private static final int TOKEN_BYTES = 32;

    private final CurrentUserService currentUserService;
    private final OperationProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, OperationStreamTicket> tickets = new ConcurrentHashMap<>();

    public OperationStreamTicketService(
            CurrentUserService currentUserService,
            OperationProperties properties,
            Clock clock
    ) {
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.clock = clock;
    }

    public OperationStreamTicketResponse issue() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMINISTRADOR && actor.getRole() != UserRole.COORDINADOR) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "El canal operativo solo está disponible para administradores y coordinadores"
            );
        }
        Instant now = Instant.now(clock);
        removeExpired(now);
        Instant expiresAt = now.plus(properties.streamTicketTtl());
        String ticket = nextTicket();
        tickets.put(ticket, new OperationStreamTicket(actor.getOrganizationId(), actor.getId(), expiresAt));
        return new OperationStreamTicketResponse(ticket, expiresAt);
    }

    Optional<OperationStreamTicket> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        OperationStreamTicket streamTicket = tickets.remove(ticket);
        if (streamTicket == null || streamTicket.isExpired(Instant.now(clock))) {
            return Optional.empty();
        }
        return Optional.of(streamTicket);
    }

    private String nextTicket() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void removeExpired(Instant now) {
        tickets.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}
