package pe.rutafija.operation.application;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import pe.rutafija.operation.api.dto.AssignmentReportResponse;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.api.dto.AvailabilityReportResponse;
import pe.rutafija.operation.api.dto.IncidentReportResponse;
import pe.rutafija.operation.api.dto.IncidentResponse;
import pe.rutafija.operation.api.dto.StatusCountResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Genera artefactos descargables únicamente a partir de los reportes persistidos y autorizados. */
@Service
public class ReportExportService {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final ReportService reportService;

    public ReportExportService(ReportService reportService) {
        this.reportService = reportService;
    }

    public ReportExport availability(ReportExportFormat format) {
        AvailabilityReportResponse report = reportService.availability();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Asignaciones futuras programadas", Long.toString(report.futureScheduledAssignments())));
        rows.add(List.of("Servicios en curso", Long.toString(report.assignmentsInService())));
        rows.add(List.of("Incidencias abiertas o en seguimiento", Long.toString(report.openIncidents())));
        report.drivers().forEach(count -> rows.add(List.of("Conductor - " + count.status(), Long.toString(count.total()))));
        report.vehicles().forEach(count -> rows.add(List.of("Vehículo - " + count.status(), Long.toString(count.total()))));
        return export(
                format,
                new ReportDataset(
                        "Disponibilidad operativa",
                        "Generado " + report.generatedAt(),
                        List.of("Indicador", "Valor"),
                        rows
                ),
                "disponibilidad-" + report.generatedAt().toString().replaceAll("[:.]", "-")
        );
    }

    public ReportExport assignments(ReportExportFormat format, LocalDate from, LocalDate to) {
        AssignmentReportResponse report = reportService.assignments(from, to);
        List<List<String>> rows = new ArrayList<>();
        report.items().forEach(item -> rows.add(assignmentRow(item)));
        appendTotals(rows, report.totalsByStatus());
        return export(
                format,
                new ReportDataset(
                        "Asignaciones operativas",
                        "Periodo " + report.from() + " a " + report.to(),
                        List.of("Estado", "Conductor", "Grupo", "Vehículo", "Origen", "Destino", "Inicio programado", "Fin programado"),
                        rows
                ),
                "asignaciones-" + report.from() + "-" + report.to()
        );
    }

    public ReportExport incidents(ReportExportFormat format, LocalDate from, LocalDate to) {
        IncidentReportResponse report = reportService.incidents(from, to);
        List<List<String>> rows = new ArrayList<>();
        report.items().forEach(item -> rows.add(incidentRow(item)));
        appendTotals(rows, report.totalsByStatus());
        appendTotals(rows, report.totalsByCategory());
        return export(
                format,
                new ReportDataset(
                        "Incidencias operativas",
                        "Periodo " + report.from() + " a " + report.to(),
                        List.of("Estado", "Categoría", "Conductor", "Descripción", "Reportada", "Seguimiento"),
                        rows
                ),
                "incidencias-" + report.from() + "-" + report.to()
        );
    }

    private ReportExport export(ReportExportFormat format, ReportDataset dataset, String basename) {
        return switch (format) {
            case PDF -> new ReportExport(asPdf(dataset), MediaType.APPLICATION_PDF, basename + ".pdf");
            case XLSX -> new ReportExport(asXlsx(dataset), XLSX_MEDIA_TYPE, basename + ".xlsx");
        };
    }

    private List<String> assignmentRow(AssignmentResponse item) {
        return List.of(
                item.status().name(),
                item.driverName(),
                item.groupName(),
                item.vehiclePlate(),
                item.originText(),
                item.destinationText(),
                item.scheduledAt().toString(),
                item.scheduledEndAt().toString()
        );
    }

    private List<String> incidentRow(IncidentResponse item) {
        return List.of(
                item.status().name(),
                item.category().name(),
                item.driverName(),
                item.description(),
                item.reportedAt().toString(),
                item.followUpNote() == null ? "" : item.followUpNote()
        );
    }

    private void appendTotals(List<List<String>> rows, List<StatusCountResponse> totals) {
        totals.forEach(total -> rows.add(List.of("TOTAL " + total.status(), Long.toString(total.total()))));
    }

    private byte[] asPdf(ReportDataset dataset) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (PdfLineWriter writer = new PdfLineWriter(document)) {
                writer.line(dataset.title(), true);
                writer.line(dataset.subtitle(), false);
                writer.line("", false);
                writer.line(String.join(" | ", dataset.headers()), true);
                if (dataset.rows().isEmpty()) {
                    writer.line("Sin registros para los criterios seleccionados.", false);
                } else {
                    dataset.rows().forEach(row -> writer.lineUnchecked(String.join(" | ", row), false));
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("No fue posible generar el PDF del reporte", exception);
        }
    }

    private byte[] asXlsx(ReportDataset dataset) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIndex = 0;
            writeRow(sheet, rowIndex++, List.of(dataset.title()), null);
            writeRow(sheet, rowIndex++, List.of(dataset.subtitle()), null);
            rowIndex++;
            writeRow(sheet, rowIndex++, dataset.headers(), headerStyle);
            if (dataset.rows().isEmpty()) {
                writeRow(sheet, rowIndex, List.of("Sin registros para los criterios seleccionados."), null);
            } else {
                for (List<String> values : dataset.rows()) {
                    writeRow(sheet, rowIndex++, values, null);
                }
            }
            for (int column = 0; column < dataset.headers().size(); column++) {
                sheet.setColumnWidth(column, 20 * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("No fue posible generar el archivo XLSX del reporte", exception);
        } finally {
            workbook.dispose();
        }
    }

    private void writeRow(Sheet sheet, int rowIndex, List<String> values, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(values.get(index) == null ? "" : values.get(index));
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private record ReportDataset(String title, String subtitle, List<String> headers, List<List<String>> rows) {
    }

    private static final class PdfLineWriter implements AutoCloseable {

        private static final float MARGIN = 42f;
        private static final float LINE_HEIGHT = 12f;
        private static final int MAX_LINE_LENGTH = 110;

        private final PDDocument document;
        private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream content;
        private float currentY;

        private PdfLineWriter(PDDocument document) throws IOException {
            this.document = document;
            nextPage();
        }

        private void line(String value, boolean emphasized) throws IOException {
            for (String part : wrap(safePdfText(value))) {
                if (currentY < MARGIN) {
                    nextPage();
                }
                content.beginText();
                content.setFont(emphasized ? bold : normal, emphasized ? 11f : 8.5f);
                content.newLineAtOffset(MARGIN, currentY);
                content.showText(part);
                content.endText();
                currentY -= LINE_HEIGHT;
            }
        }

        private void lineUnchecked(String value, boolean emphasized) {
            try {
                line(value, emphasized);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void nextPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            currentY = page.getMediaBox().getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
            }
        }

        private static List<String> wrap(String value) {
            if (value.isBlank()) {
                return List.of(" ");
            }
            List<String> parts = new ArrayList<>();
            String remaining = value;
            while (remaining.length() > MAX_LINE_LENGTH) {
                int breakAt = remaining.lastIndexOf(' ', MAX_LINE_LENGTH);
                if (breakAt < 1) {
                    breakAt = MAX_LINE_LENGTH;
                }
                parts.add(remaining.substring(0, breakAt).strip());
                remaining = remaining.substring(breakAt).stripLeading();
            }
            parts.add(remaining);
            return parts;
        }

        private static String safePdfText(String value) {
            StringBuilder safe = new StringBuilder();
            for (char character : (value == null ? "" : value).replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').toCharArray()) {
                if (character >= 32 && character <= 255) {
                    safe.append(character);
                }
            }
            return safe.toString();
        }
    }
}
