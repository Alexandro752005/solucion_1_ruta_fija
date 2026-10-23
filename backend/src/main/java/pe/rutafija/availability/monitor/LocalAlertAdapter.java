package pe.rutafija.availability.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import pe.rutafija.availability.domain.AvailabilityPolicy;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/** Local test outbox only. A durable file is NOT an external alert or acknowledgement by a responsible person. */
public final class LocalAlertAdapter {
    public record Receipt(String reference, String sha256, String result) { }
    private final Path directory;
    private final ObjectMapper json = new ObjectMapper();

    public LocalAlertAdapter(Path runDirectory) throws IOException {
        directory = runDirectory.resolve("local-test-alerts");
        Files.createDirectory(directory);
    }

    public Receipt record(AvailabilityPolicy.Event event) throws IOException {
        String reference = event.incidentId() + "-" + event.kind() + ".json";
        var entry = new LinkedHashMap<String, Object>();
        entry.put("formatVersion", 1); entry.put("transport", "LOCAL_TEST_ONLY");
        entry.put("externalDeliveryAccredited", false); entry.put("humanAcknowledgementAccredited", false);
        entry.put("event", MonitorJournal.eventFields(event));
        byte[] encoded = json.writeValueAsBytes(entry);
        Path file = directory.resolve(reference);
        try (var channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = java.nio.ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (java.nio.file.FileAlreadyExistsException existing) {
            if (!MessageDigest.isEqual(Files.readAllBytes(file), encoded)) throw new IOException("LOCAL_ALERT_CONFLICT");
            return new Receipt(reference, sha256(encoded), "ALREADY_RECORDED_LOCAL_ONLY");
        }
        return new Receipt(reference, sha256(encoded), "RECORDED_LOCAL_ONLY_NOT_EXTERNALLY_DELIVERED");
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
}
