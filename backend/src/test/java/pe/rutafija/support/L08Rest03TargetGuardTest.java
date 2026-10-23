package pe.rutafija.support;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class L08Rest03TargetGuardTest {
    @Test void permitsOnlyTheExplicitNewDestination(){
        assertThat(L08Rest03RestoreCli.authorizedTarget("NEW_EMPTY_CLUSTER_55440","jdbc:postgresql://127.0.0.1:55440/ruta_fija_test")).isTrue();
    }
    @Test void refusesSharedDevelopmentPilotOtherDestinationsAndUrlOptions(){
        for(String url:java.util.List.of("jdbc:postgresql://127.0.0.1:5432/ruta_fija_test","jdbc:postgresql://127.0.0.1:55438/ruta_fija_test","jdbc:postgresql://127.0.0.1:55439/ruta_fija_test","jdbc:postgresql://127.0.0.1:55440/ruta_fija_piloto","jdbc:postgresql://127.0.0.1:55440/solucion_ruta_fija_1","jdbc:postgresql://127.0.0.1:55440/ruta_fija_test_l08_locale","jdbc:postgresql://localhost:55440/ruta_fija_test","jdbc:postgresql://127.0.0.1:55440/ruta_fija_test?currentSchema=public"))
            assertThat(L08Rest03RestoreCli.authorizedTarget("NEW_EMPTY_CLUSTER_55440",url)).isFalse();
    }
    @Test void refusesImplicitOldOrLocaleAuthorization(){
        for(String approval:java.util.Arrays.asList(null,"","NEW_EMPTY_CLUSTER_55438","NEW_LOCALE_MATCHED_DATABASE_55440","OK_VERDE"))
            assertThat(L08Rest03RestoreCli.authorizedTarget(approval,"jdbc:postgresql://127.0.0.1:55440/ruta_fija_test")).isFalse();
    }
}
