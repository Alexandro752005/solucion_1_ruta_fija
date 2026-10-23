package pe.rutafija.adminlifecycle.api;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import pe.rutafija.personidentity.api.PersonIdentityContract.IdentityReview;
import java.time.Instant;
import java.util.UUID;

public final class AdminLifecycleContract {
    private AdminLifecycleContract() { }
    public record Create(@NotNull UUID organizationId,@NotBlank @Email @Size(max=180) String email,
                         @NotBlank @Size(max=160) String fullName,@NotBlank @Size(max=500) String reason,
                         @NotNull @Valid IdentityReview identityReview) {
        @Override public String toString(){return "Create[redacted]";}
    }
    public record Replacement(@NotBlank @Email @Size(max=180) String email,
                              @NotBlank @Size(max=160) String fullName,
                              @NotBlank @Pattern(regexp="PRIOR_SECURE_CONTACT|IN_PERSON") String verificationMethod,
                              @NotBlank @Size(max=160) String verificationReference,
                              @NotBlank @Size(max=500) String reason,
                              @NotNull @AssertTrue Boolean identityChecked,
                              @NotNull @AssertTrue Boolean replacementAuthorized,
                              @NotNull @Valid IdentityReview identityReview) {
        @Override public String toString(){return "Replacement[redacted]";}
    }
    public record Rotate(@NotBlank @Size(max=500) String reason) { }
    public record Claim(@NotBlank @Pattern(regexp="ac1\\.[A-Za-z0-9_-]{43}") String credential,
                        @NotBlank @Size(min=12,max=72) String newPassword) {
        @Override public String toString(){return "Claim[redacted]";}
    }
    public record OtpToken(@NotBlank @Size(max=100) String verificationToken) {
        @Override public String toString(){return "OtpToken[redacted]";}
    }
    public record VerifyOtp(@NotBlank @Size(max=100) String verificationToken,
                            @NotBlank @Pattern(regexp="[0-9]{6}") String code) {
        @Override public String toString(){return "VerifyOtp[redacted]";}
    }
    public record Created(UUID adminUserId,UUID organizationId,UUID activationId,String status,
                          String credential,Instant credentialExpiresAt) {
        @Override public String toString(){return "Created[adminUserId="+adminUserId+",credential=redacted]";}
    }
    public record Claimed(UUID activationId,String activationToken,Instant expiresAt,String status) {
        @Override public String toString(){return "Claimed[activationId="+activationId+",activationToken=redacted]";}
    }
    public record Summary(UUID adminUserId,UUID organizationId,boolean active,UUID activationId,String status,
                          UUID replacesAdminUserId,Instant credentialExpiresAt,Instant createdAt,boolean identityVerified) { }
    public record Status(UUID activationId,UUID adminUserId,UUID organizationId,String status,
                         boolean passwordSet,boolean emailVerified,boolean termsAccepted,Instant expiresAt,
                         boolean replacesAdmin,boolean identityVerified) { }
}
