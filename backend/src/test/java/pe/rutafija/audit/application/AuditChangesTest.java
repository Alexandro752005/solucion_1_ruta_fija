package pe.rutafija.audit.application;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AuditChangesTest {
    @Test void onlyChangedTechnicalValuesHaveBeforeAndAfter(){UUID old=UUID.randomUUID(),next=UUID.randomUUID();var values=AuditChanges.fields("groupId",old,next,"active",false,true,"unchanged",1,1);assertThat(values).hasSize(2);assertThat(values.getFirst()).containsEntry("before",old).containsEntry("after",next);}
    @Test void freeTextCannotAccidentallyEnterAnUnmaskedSnapshot(){assertThatThrownBy(()->AuditChanges.fields("fullName","SYNTHETIC_BEFORE","SYNTHETIC_AFTER")).isInstanceOf(IllegalArgumentException.class);var map=AuditChanges.masked("fullName","SYNTHETIC_BEFORE","SYNTHETIC_AFTER");assertThat(map.toString()).doesNotContain("SYNTHETIC_BEFORE","SYNTHETIC_AFTER");assertThat(new AuditMetadataPolicy().sanitize(Map.of("changes",List.of(map))).toString()).contains("REDACTADO");}
    @Test void absentValuesAreExplicitAndMalformedTriplesFail(){assertThat(AuditChanges.fields("groupId",null,UUID.randomUUID()).getFirst()).containsEntry("before","NOT_SET");assertThatThrownBy(()->AuditChanges.fields("status",true)).isInstanceOf(IllegalArgumentException.class);}
}
