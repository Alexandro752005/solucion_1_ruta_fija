package pe.rutafija.availability.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import pe.rutafija.availability.domain.AvailabilityPolicy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Append-only sanitized JSONL and atomic heartbeat. No request/response body or exception text is persisted. */
public final class MonitorJournal implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final Path directory, journal;
    private final FileChannel output;
    private final UUID runId;
    private long journalSequence;
    private boolean closed;

    public MonitorJournal(Path outputRoot, UUID runId) throws IOException {
        this.runId = runId;
        Files.createDirectories(outputRoot);
        directory = outputRoot.resolve("monitor-" + runId);
        Files.createDirectory(directory); // Never reuse or overwrite an existing run.
        journal = directory.resolve("events.jsonl");
        output = FileChannel.open(journal, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public synchronized void append(String type, Instant at, Map<String, ?> fields) throws IOException {
        if (closed) throw new IOException("JOURNAL_ALREADY_CLOSED");
        var record = new LinkedHashMap<String, Object>();
        record.put("formatVersion", 1); record.put("runId", runId.toString());
        record.put("journalSequence", ++journalSequence); record.put("type", type); record.put("at", at.toString());
        record.put("details", fields);
        byte[] encoded = (json.writeValueAsString(record) + "\n").getBytes(StandardCharsets.UTF_8);
        var bytes = ByteBuffer.wrap(encoded);
        while (bytes.hasRemaining()) output.write(bytes);
        output.force(true);
    }

    public synchronized void heartbeat(Instant at, long sampleSequence, boolean healthy,
                                       boolean incidentOpen, boolean suspected) throws IOException {
        var record = new LinkedHashMap<String, Object>();
        record.put("formatVersion", 1); record.put("runId", runId.toString());
        record.put("observedAt", at.toString()); record.put("sampleSequence", sampleSequence);
        record.put("allRequiredProbesHealthy", healthy); record.put("incidentOpen", incidentOpen);
        record.put("suspectedFailure", suspected); record.put("externalCoverageAccredited", false);
        Path temporary = directory.resolve("heartbeat-" + UUID.randomUUID() + ".tmp");
        try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var bytes = ByteBuffer.wrap(json.writeValueAsBytes(record));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        try {
            Files.move(temporary, directory.resolve("heartbeat.json"), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            // An incomplete heartbeat must not be published as if atomicity had been verified.
            throw new IOException("ATOMIC_HEARTBEAT_REPLACEMENT_REQUIRED");
        }
    }

    public static Map<String, Object> eventFields(AvailabilityPolicy.Event event) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("kind", event.kind().name()); fields.put("incidentId", event.incidentId().toString());
        fields.put("firstFailureAt", event.firstFailureAt().toString());
        fields.put("confirmedAt", event.confirmedAt().toString()); fields.put("observedAt", event.observedAt().toString());
        fields.put("detectionSeconds", event.detectionSeconds()); fields.put("recoverySeconds", event.recoverySeconds());
        fields.put("detectionMillis", java.time.Duration.between(event.firstFailureAt(), event.confirmedAt()).toMillis());
        fields.put("recoveryMillis", event.kind() == AvailabilityPolicy.Kind.VERIFIED_RECOVERY
            ? java.time.Duration.between(event.confirmedAt(), event.observedAt()).toMillis() : null);
        fields.put("detectionWithinFiveMinutes", event.detectionWithinFiveMinutes());
        fields.put("recoveryWithinThirtyMinutes", event.recoveryWithinThirtyMinutes());
        fields.put("measurementOrigin", "OBSERVED_HTTP_SAMPLES_NOT_ACTUAL_UNOBSERVED_OUTAGE_START");
        return fields;
    }

    public UUID runId() { return runId; }
    public Path directory() { return directory; }
    public Path journal() { return journal; }
    public synchronized String sha256() throws IOException {
        output.force(true);
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(journal);var stream=new java.security.DigestInputStream(input,digest)){
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    @Override public synchronized void close() throws IOException {
        if (!closed) { output.force(true); output.close(); closed = true; }
    }
}
