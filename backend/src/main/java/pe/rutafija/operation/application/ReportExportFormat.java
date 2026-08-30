package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.util.Locale;

public enum ReportExportFormat {
    PDF,
    XLSX;

    public static ReportExportFormat from(String value) {
        try {
            return valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "El formato de exportación debe ser PDF o XLSX"
            );
        }
    }
}
