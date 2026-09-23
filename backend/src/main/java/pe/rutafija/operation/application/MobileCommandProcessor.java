package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.MobileCommandReceipt;
import pe.rutafija.operation.domain.MobileCommandResultStatus;
import pe.rutafija.operation.domain.MobileCommandType;
import pe.rutafija.operation.infrastructure.MobileCommandReceiptRepository;
import pe.rutafija.operation.infrastructure.PostgresMobileEventLock;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.MobileDriverActor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes an assignment command exactly once per global event id. PostgreSQL
 * advisory locking closes the read-then-insert race without keeping a lock past
 * the transaction; the durable receipt is saved in the same transaction as the
 * business transition.
 */
@Service
public class MobileCommandProcessor {

    private static final Duration MAX_CLIENT_CLOCK_AHEAD = Duration.ofMinutes(5);

    private final MobileCommandReceiptRepository receiptRepository;
    private final PostgresMobileEventLock eventLock;
    private final Clock clock;

    public MobileCommandProcessor(
            MobileCommandReceiptRepository receiptRepository,
            PostgresMobileEventLock eventLock,
            Clock clock
    ) {
        this.receiptRepository = receiptRepository;
        this.eventLock = eventLock;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public <T> MobileCommandExecution<T> execute(
            MobileDriverActor actor,
            UUID eventId,
            Instant clientOccurredAt,
            MobileCommandType commandType,
            Assignment receiptAssignment,
            String requestHash,
            Function<MobileCommandReceipt, T> replay,
            Supplier<T> apply,
            Function<T, String> serverState
    ) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(clientOccurredAt);
        Objects.requireNonNull(commandType);
        Objects.requireNonNull(receiptAssignment);
        Objects.requireNonNull(requestHash);

        Instant serverNow = Instant.now(clock);
        if (clientOccurredAt.isAfter(serverNow.plus(MAX_CLIENT_CLOCK_AHEAD))) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "occurredAt no puede superar la tolerancia del reloj del servidor"
            );
        }
        eventLock.lock(eventId);
        MobileCommandReceipt existing = receiptRepository.findByEventId(eventId).orElse(null);
        if (existing != null) {
            assertSameCommand(existing, actor, commandType, receiptAssignment, requestHash);
            if (existing.getResultStatus() == MobileCommandResultStatus.APPLIED) {
                return new MobileCommandExecution<>(replay.apply(existing), true);
            }
            throw priorFailure(existing);
        }

        Instant processedAt = serverNow;
        if (processedAt.isBefore(clientOccurredAt)) {
            // The receipt preserves client metadata, while the database invariant
            // remains valid even when a device clock is slightly ahead.
            processedAt = clientOccurredAt;
        }
        try {
            T result = apply.get();
            receiptRepository.saveAndFlush(MobileCommandReceipt.applied(
                    eventId,
                    actor.user().getOrganization(),
                    actor.driver(),
                    commandType,
                    receiptAssignment,
                    requestHash,
                    serverState.apply(result),
                    clientOccurredAt,
                    processedAt
            ));
            return new MobileCommandExecution<>(result, false);
        } catch (ApplicationException exception) {
            receiptRepository.saveAndFlush(MobileCommandReceipt.rejected(
                    eventId,
                    actor.user().getOrganization(),
                    actor.driver(),
                    commandType,
                    receiptAssignment,
                    requestHash,
                    (short) exception.getStatus().value(),
                    receiptAssignment.getStatus().name(),
                    exception.getCode().name(),
                    clientOccurredAt,
                    processedAt
            ));
            throw exception;
        }
    }

    public String hash(String canonicalRequest) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 debe estar disponible en Java", exception);
        }
    }

    private void assertSameCommand(
            MobileCommandReceipt existing,
            MobileDriverActor actor,
            MobileCommandType commandType,
            Assignment assignment,
            String requestHash
    ) {
        if (!existing.getOrganization().getId().equals(actor.user().getOrganizationId())
                || !existing.getDriver().getId().equals(actor.driver().getId())
                || existing.getCommandType() != commandType
                || existing.getAssignment() == null
                || !existing.getAssignment().getId().equals(assignment.getId())
                || !existing.getRequestHash().equals(requestHash)) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.MOBILE_EVENT_CONFLICT,
                    "El clientEventId ya pertenece a otro comando movil"
            );
        }
    }

    private ApplicationException priorFailure(MobileCommandReceipt receipt) {
        HttpStatus status = HttpStatus.resolve(receipt.getHttpStatus());
        ErrorCode code;
        try {
            code = ErrorCode.valueOf(receipt.getErrorCode());
        } catch (IllegalArgumentException | NullPointerException exception) {
            code = ErrorCode.INTERNAL_ERROR;
        }
        return new ApplicationException(
                status == null ? HttpStatus.CONFLICT : status,
                code,
                "El comando movil ya fue rechazado sin aplicar cambios"
        );
    }
}
