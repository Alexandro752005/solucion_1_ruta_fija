package pe.rutafija.personidentity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class PersonIdentityContract {
    private PersonIdentityContract() { }
    public record IdentityReview(@NotBlank @Pattern(regexp="DNI") String documentType,
            @NotBlank @Pattern(regexp="[0-9]{8}") String documentNumber,
            @NotBlank @Pattern(regexp="IN_PERSON|PRIOR_SECURE_CONTACT") String method,
            @NotBlank @Size(max=160) String reference,
            @NotNull @AssertTrue Boolean originalReviewed,@NotNull @AssertTrue Boolean identityConfirmed) {
        @Override public String toString(){return "IdentityReview[redacted]";}
    }
    public record SelfEnrollment(@NotBlank @Size(max=72) String currentPassword,
            @NotNull @AssertTrue Boolean ownerDeclaration,@NotNull @Valid IdentityReview identityReview) {
        @Override public String toString(){return "SelfEnrollment[redacted]";}
    }
    public record AdminReview(@NotNull @Valid IdentityReview identityReview,@NotBlank @Size(max=500) String reason) {
        @Override public String toString(){return "AdminReview[redacted]";}
    }
    public record Status(UUID userId,boolean identityVerified) { }
}
