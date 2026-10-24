package pe.rutafija.operation.application;

import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReportExportResourceTest {
    @TempDir Path temporary;

    @Test void streamingExportRetainsRowsAndDeletesItsTemporaryFilesWhenClosed() throws Exception {
        var source=mock(ReportService.class);
        var export=new ReportExportService(source);
        var rows=IntStream.range(0,250).mapToObj(i->List.of("synthetic "+i,"value "+i)).toList();
        TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy(temporary.toFile()));
        try{
            var result=export.tabular(ReportExportFormat.XLSX,"Synthetic title","Synthetic subtitle",List.of("Name","Value"),rows,"synthetic-report");
            assertThat(result.filename()).isEqualTo("synthetic-report.xlsx");
            try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(result.content()))){
                var sheet=workbook.getSheet("Reporte");
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Synthetic title");
                assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Name");
                assertThat(sheet.getRow(253).getCell(1).getStringCellValue()).isEqualTo("value 249");
            }
            try(var files=Files.list(temporary)){assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();}
            verifyNoInteractions(source);
        }finally{TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy());}
    }
}
