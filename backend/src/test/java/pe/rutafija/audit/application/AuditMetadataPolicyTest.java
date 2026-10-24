package pe.rutafija.audit.application;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AuditMetadataPolicyTest {
    private final AuditMetadataPolicy policy=new AuditMetadataPolicy();
    @Test void preservesTypedReferencesStatusVersionsAndDates(){
        UUID id=UUID.randomUUID();var source=Map.<String,Object>of("driverId",id,"version",3,"active",false,"status","CONFIRMED","occurredAt",Instant.parse("2026-10-21T00:00:00Z"));
        assertThat(policy.sanitize(source)).containsEntry("driverId",id.toString()).containsEntry("version",3).containsEntry("active",false).containsEntry("status","CONFIRMED").containsEntry("occurredAt","2026-10-21T00:00:00Z");
    }
    @Test void rejectsNestedSecretsAndUnknownFreeText(){
        var result=policy.sanitize(Map.of("details",Map.of("password","FAKE_SECRET","context",List.of(Map.of("email","synthetic@example.test","status","OPEN"))),"unreviewed","FAKE_PII","description","FAKE_NOTE"));
        assertThat(result.toString()).doesNotContain("FAKE_SECRET","synthetic@example.test","FAKE_PII","FAKE_NOTE").contains("OPEN");
    }
    @Test void masksPersonalChangesEvenWhenUppercaseOrNumeric(){
        var rows=List.of(Map.of("field","fullName","before","SYNTHETIC","after","OTHER"),Map.of("field","documentNumber","before",12345678,"after",87654321));
        var result=policy.sanitize(Map.of("changes",rows));assertThat(result.toString()).doesNotContain("SYNTHETIC","OTHER","12345678","87654321").contains("[REDACTADO]");
    }
    @Test void preservesPublicFingerprintButNeverTokenHashesOrPrivateMaterial(){
        String hash="a".repeat(64);assertThat(policy.sanitize(Map.of("publicKeySha256",hash,"tokenHash",hash,"privateKey","FAKE_KEY"))).containsOnlyKeys("publicKeySha256").containsEntry("publicKeySha256",hash);
    }
    @Test void excludesFreeReasonsReferencesCoordinatesAndLineBreaks(){
        var result=policy.sanitize(Map.of("reason","SYNTHETICPERSON","evidenceReference","SYNTHETICPERSON","latitude",-6.7,"status","OPEN\nFAKE_SECRET","phoneCount",12345678));assertThat(result).isEmpty();
        assertThat(policy.sanitize(Map.of("reason","AUTH_TOKEN_INVALID"))).containsEntry("reason","AUTH_TOKEN_INVALID");
    }
    @Test void boundsCollectionsAndSupportsNullKeysWithoutMutatingInput(){
        var values=new LinkedHashMap<String,Object>();values.put(null,"FAKE_SECRET");values.put("status","OPEN");values.put("details",Collections.nCopies(300,Map.of("status","OPEN")));
        var result=policy.sanitize(values);assertThat((List<?>)result.get("details")).hasSize(128);assertThat(values).containsKey(null);assertThatThrownBy(()->result.put("status","CLOSED")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void rejectsPiiInOtherwiseAllowedStringFields(){assertThat(policy.sanitize(Map.of("status","synthetic@example.test","result","123456","operationKey","person@example.test"))).isEmpty();}
    @Test void preservesNonPersonalStructuredDifference(){var result=policy.sanitize(Map.of("changes",List.of(Map.of("field","status","before","OPEN","after","RESOLVED"))));assertThat(result.toString()).contains("OPEN","RESOLVED","status");}
}
