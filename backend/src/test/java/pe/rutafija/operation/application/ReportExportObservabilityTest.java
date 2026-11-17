package pe.rutafija.operation.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReportExportObservabilityTest {
    private final ReportService source=mock(ReportService.class);
    private final ReportExportService service=new ReportExportService(source);
    private final Logger logger=(Logger)LoggerFactory.getLogger(ReportExportService.class);
    private final ListAppender<ILoggingEvent> appender=new ListAppender<>();
    private Level originalLevel;
    private boolean originalAdditive;

    @BeforeEach void captureOnlyExportLogger() {
        originalLevel=logger.getLevel();originalAdditive=logger.isAdditive();
        appender.setContext(logger.getLoggerContext());appender.start();
        logger.addAppender(appender);logger.setLevel(Level.DEBUG);logger.setAdditive(false);
    }
    @AfterEach void restoreLogger() {
        logger.detachAppender(appender);appender.stop();
        logger.setLevel(originalLevel);logger.setAdditive(originalAdditive);
        verifyNoInteractions(source);
    }
    @Test void csvPreservesBomCrLfQuotesAndFormulaEscaping() throws Exception {
        var file=service.tabular(ReportExportFormat.CSV,"Synthetic title","Synthetic subtitle",
            List.of("Name","Value"),List.of(List.of("a,\"b","=1+1"),Arrays.asList(null,"@SUM(1)")),"synthetic-report");
        String expected="\uFEFF\"Synthetic title\"\r\n\"Synthetic subtitle\"\r\n\"Name\",\"Value\"\r\n\"a,\"\"b\",\"'=1+1\"\r\n\"\",\"'@SUM(1)\"\r\n";
        assertThat(file.content()).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
        assertThat(file.contentType().toString()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(file.filename()).isEqualTo("synthetic-report.csv");record(file);
    }
    @Test void xlsxRetainsCellTextAndHeaderStructure() throws Exception {
        var file=service.tabular(ReportExportFormat.XLSX,"Synthetic title","Synthetic subtitle",
            List.of("Name","Value"),List.of(List.of("=1+1","a,\"b")),"synthetic-report");
        assertThat(file.contentType().toString()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            var sheet=workbook.getSheet("Reporte");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Synthetic title");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Name");
            assertThat(sheet.getRow(4).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("a,\"b");
        }record(file);
    }
    @Test void pdfRetainsReadableDataset() throws Exception {
        var file=service.tabular(ReportExportFormat.PDF,"Synthetic title","Synthetic subtitle",
            List.of("Name","Value"),List.of(List.of("Synthetic row","42")),"synthetic-report");
        assertThat(file.contentType().toString()).isEqualTo("application/pdf");
        try(var document=Loader.loadPDF(file.content())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains("Synthetic title","Synthetic subtitle","Name | Value","Synthetic row | 42");
        }record(file);
    }
    @Test void successLogsOnlyFormatResultAndDuration() {
        service.tabular(ReportExportFormat.CSV,"DO_NOT_LOG_TITLE","DO_NOT_LOG_SUBTITLE",
            List.of("DO_NOT_LOG_HEADER"),List.of(List.of("DO_NOT_LOG_CONTENT")),"DO_NOT_LOG_FILENAME");
        assertThat(appender.list).hasSize(1);
        check(appender.list.getFirst(),Level.DEBUG,"CSV","SUCCESS");
    }
    @Test void generationFailureLogsNoPayloadOrThrowableAndPreservesException() {
        var failure=assertThrows(IllegalArgumentException.class,()->service.tabular(ReportExportFormat.XLSX,
            "DO_NOT_LOG_TITLE","DO_NOT_LOG_SUBTITLE",List.of("Name"),List.of(List.of("S".repeat(33000))),"DO_NOT_LOG_FILENAME"));
        assertThat(failure.getMessage()).contains("32767");
        RuntimeException original=new IllegalStateException("DO_NOT_LOG_EXCEPTION_MESSAGE");
        List<String> brokenRow=new AbstractList<>() {
            @Override public String get(int index){throw original;}
            @Override public int size(){return 1;}
        };
        var rethrown=assertThrows(IllegalStateException.class,()->service.tabular(ReportExportFormat.XLSX,
            "DO_NOT_LOG_TITLE","DO_NOT_LOG_SUBTITLE",List.of("Name"),List.of(brokenRow),"DO_NOT_LOG_FILENAME"));
        assertSame(original,rethrown);
        assertThat(appender.list).hasSize(2);
        for(var event:appender.list)check(event,Level.WARN,"XLSX","FAILURE");
    }
    private void check(ILoggingEvent event,Level level,String format,String result) {
        assertThat(event.getLevel()).isEqualTo(level);
        assertThat(event.getFormattedMessage()).matches("report_export format="+format+" result="+result+" duration_nanos=\\d+");
        assertThat(event.getFormattedMessage()).doesNotContain("DO_NOT_LOG","33000","32767");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getArgumentArray()).hasSize(3);
        assertThat(event.getArgumentArray()[0]).isInstanceOf(ReportExportFormat.class);
        assertThat(event.getArgumentArray()[2]).isInstanceOf(Long.class);
    }
    private void record(ReportExport file) throws Exception {
        String target=System.getProperty("rubrica.artifacts");
        if(target!=null){Path directory=Path.of(target);Files.createDirectories(directory);Files.write(directory.resolve(file.filename()),file.content());}
    }
}
