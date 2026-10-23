package pe.rutafija.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.rutafija.fleet.api.dto.DriverResponse;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.identity.api.dto.AuthenticatedUserResponse;
import pe.rutafija.identity.api.dto.MobileAuthenticatedUserResponse;
import pe.rutafija.identity.api.dto.UserResponse;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.api.dto.mobile.MobileDriverProfileResponse;
import pe.rutafija.organization.domain.Organization;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataProjectionTest {
    @Test
    void ordinaryCrmAndMobileProjectionsContainReferencesAndNoPersonalFields() throws Exception {
        var org = Organization.active("Synthetic organization", "Synthetic organization", "America/Lima");
        var user = AppUser.organizationUser(org, "synthetic.pii@rutafija.test", "synthetic-hash",
                "Synthetic Private Name", UserRole.CONDUCTOR);
        user.updateDetails(user.getEmail(), user.getFullName(), "919876543", user.getRole());
        var group = TransportGroup.active(org, "Synthetic group", "Synthetic fixture");
        var driver = Driver.register(org, user, group, user.getFullName(), user.getPhone(),
                "DNI", "98765432", "LIC-PRIVATE-123");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var response = mapper.writeValueAsString(List.of(
                AuthenticatedUserResponse.from(user), UserResponse.from(user), DriverResponse.from(driver),
                MobileAuthenticatedUserResponse.from(user, driver), MobileDriverProfileResponse.from(driver, List.of())));

        assertThat(response).contains("CUENTA-" + user.getId(), "CONDUCTOR-" + driver.getId());
        assertThat(response).doesNotContain(user.getEmail(), user.getFullName(), user.getPhone(),
                driver.getDocumentNumber(), driver.getLicenseNumber(), "synthetic-hash");
    }
}
