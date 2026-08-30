package pe.rutafija.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit(
            RateLimitExceededException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = errorBody(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                List.of(),
                request
        );
        return ResponseEntity.status(exception.getStatus())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException exception,
            HttpServletRequest request
    ) {
        return response(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "La solicitud contiene datos inválidos",
                details,
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "La solicitud contiene datos inválidos",
                details,
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                exception.getMessage() == null || exception.getMessage().isBlank()
                        ? "La solicitud contiene datos inválidos"
                        : exception.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_REQUEST,
                "El cuerpo de la solicitud no es válido",
                List.of(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "La solicitud contiene datos inválidos",
                List.of(new FieldErrorDetail(exception.getName(), "El valor no tiene el formato esperado")),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "La solicitud contiene datos inválidos",
                List.of(new FieldErrorDetail(exception.getParameterName(), "El parámetro es obligatorio")),
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = errorBody(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED,
                "El método HTTP no está permitido para este recurso",
                List.of(),
                request
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .cacheControl(CacheControl.noStore());
        Set<HttpMethod> supportedMethods = exception.getSupportedHttpMethods();
        if (supportedMethods != null && !supportedMethods.isEmpty()) {
            builder.allow(supportedMethods.toArray(HttpMethod[]::new));
        }
        return builder.body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.MEDIA_TYPE_NOT_SUPPORTED,
                "El tipo de contenido de la solicitud no está soportado",
                List.of(),
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe",
                List.of(),
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn("Persistence constraint rejected the request");
        boolean schedulingConflict = isSchedulingConstraint(exception);
        return response(
                HttpStatus.CONFLICT,
                schedulingConflict ? ErrorCode.ASSIGNMENT_SCHEDULE_CONFLICT : ErrorCode.RESOURCE_CONFLICT,
                schedulingConflict
                        ? "El conductor o vehículo ya tiene una asignación en ese intervalo"
                        : "La operación entra en conflicto con los datos existentes",
                List.of(),
                request
        );
    }

    private boolean isSchedulingConstraint(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("ex_assignment_driver_schedule_no_overlap")
                    || message.contains("ex_assignment_vehicle_schedule_no_overlap"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                "El recurso fue modificado por otro usuario; actualice la información e inténtelo nuevamente",
                List.of(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN_ROLE,
                "No tiene permisos para realizar esta operación",
                List.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected request failure", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Ocurrió un error interno",
                List.of(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<FieldErrorDetail> errors,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(errorBody(status, code, message, errors, request));
    }

    private ApiErrorResponse errorBody(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<FieldErrorDetail> errors,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code.name(),
                message,
                request.getRequestURI(),
                CorrelationIdFilter.from(request),
                errors
        );
    }
}
