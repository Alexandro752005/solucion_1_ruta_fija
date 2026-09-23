package pe.rutafija.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable scalar result for one mobile assignment command event. */
@Entity
@Table(name = "mobile_command_receipt")
public class MobileCommandReceipt {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, updatable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 40, updatable = false)
    private MobileCommandType commandType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", updatable = false)
    private Assignment assignment;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 30)
    private MobileCommandResultStatus resultStatus;

    @Column(name = "http_status", nullable = false)
    private short httpStatus;

    @Column(name = "server_state", length = 30)
    private String serverState;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected MobileCommandReceipt() {
    }

    private MobileCommandReceipt(
            UUID eventId,
            Organization organization,
            Driver driver,
            MobileCommandType commandType,
            Assignment assignment,
            String requestHash,
            MobileCommandResultStatus resultStatus,
            short httpStatus,
            String serverState,
            String errorCode,
            Instant occurredAt,
            Instant processedAt
    ) {
        this.eventId = Objects.requireNonNull(eventId);
        this.organization = Objects.requireNonNull(organization);
        this.driver = Objects.requireNonNull(driver);
        this.commandType = Objects.requireNonNull(commandType);
        this.assignment = assignment;
        this.requestHash = Objects.requireNonNull(requestHash);
        this.resultStatus = Objects.requireNonNull(resultStatus);
        this.httpStatus = httpStatus;
        this.serverState = serverState;
        this.errorCode = errorCode;
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.processedAt = Objects.requireNonNull(processedAt);
    }

    public static MobileCommandReceipt applied(
            UUID eventId,
            Organization organization,
            Driver driver,
            MobileCommandType commandType,
            Assignment assignment,
            String requestHash,
            String serverState,
            Instant occurredAt,
            Instant processedAt
    ) {
        return new MobileCommandReceipt(
                eventId, organization, driver, commandType, assignment, requestHash,
                MobileCommandResultStatus.APPLIED, (short) 200, serverState, null, occurredAt, processedAt
        );
    }

    public static MobileCommandReceipt rejected(
            UUID eventId,
            Organization organization,
            Driver driver,
            MobileCommandType commandType,
            Assignment assignment,
            String requestHash,
            short httpStatus,
            String serverState,
            String errorCode,
            Instant occurredAt,
            Instant processedAt
    ) {
        return new MobileCommandReceipt(
                eventId, organization, driver, commandType, assignment, requestHash,
                MobileCommandResultStatus.REJECTED, httpStatus, serverState, Objects.requireNonNull(errorCode),
                occurredAt, processedAt
        );
    }

    public UUID getEventId() {
        return eventId;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Driver getDriver() {
        return driver;
    }

    public MobileCommandType getCommandType() {
        return commandType;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public MobileCommandResultStatus getResultStatus() {
        return resultStatus;
    }

    public short getHttpStatus() {
        return httpStatus;
    }

    public String getServerState() {
        return serverState;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
