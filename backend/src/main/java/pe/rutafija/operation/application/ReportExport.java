package pe.rutafija.operation.application;

import org.springframework.http.MediaType;

public record ReportExport(byte[] content, MediaType contentType, String filename) {
}
