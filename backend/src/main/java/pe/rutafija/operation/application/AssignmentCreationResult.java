package pe.rutafija.operation.application;

import pe.rutafija.operation.api.dto.AssignmentResponse;

public record AssignmentCreationResult(AssignmentResponse assignment, boolean created) {
}
