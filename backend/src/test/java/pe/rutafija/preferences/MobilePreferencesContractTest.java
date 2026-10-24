package pe.rutafija.preferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import pe.rutafija.preferences.api.MobilePreferencesContract.*;

import static org.assertj.core.api.Assertions.*;

class MobilePreferencesContractTest {
    @Test void preferencesRequireAllConcurrencyAndPresentationFields() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var errors=factory.getValidator().validate(new Update(null,null,null));
            assertThat(errors).extracting(e->e.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("version","optionalNoticeAlerts","textSize");
        }
    }
    @Test void preferencesRejectNegativeVersion() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new Update(-1L,false,TextSize.LARGE)))
                    .extracting(e->e.getPropertyPath().toString()).containsExactly("version");
        }
    }
    @Test void preferencesPermitOnlyDefinedPresentationSizesAndKeepMandatoryNotices() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            for(var size:TextSize.values()) {
                assertThat(factory.getValidator().validate(new Update(0L,false,size))).isEmpty();
                assertThat(new View(1,false,size,true).mandatoryNoticesAlwaysVisible()).isTrue();
            }
        }
    }
    @Test void preferencesUnknownSizeCannotBeDeserializedAsAValidChoice() {
        assertThatThrownBy(()->new ObjectMapper().readValue("{\"version\":0,\"optionalNoticeAlerts\":false,\"textSize\":\"HIDE_SECURITY\"}",Update.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }
}
