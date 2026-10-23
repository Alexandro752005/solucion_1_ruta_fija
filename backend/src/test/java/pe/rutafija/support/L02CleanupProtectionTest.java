package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class L02CleanupProtectionTest {
    @TempDir Path directory;
    @Test void l08MarkerCannotBeRemovedOrIgnoredByCleanupPreflight() throws Exception {
        Path marker=directory.resolve("l08-connected.active");
        assertThatCode(()->NativePostgresTestDatabase.assertNoL08ConnectedFixture(marker)).doesNotThrowAnyException();
        Files.writeString(marker,"a999ef52-d87f-498d-9251-1581cbb72562");
        assertThatThrownBy(()->NativePostgresTestDatabase.assertNoL08ConnectedFixture(marker))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("L08 connected case protected");
        assertThat(Files.readString(marker)).isEqualTo("a999ef52-d87f-498d-9251-1581cbb72562");
    }
    @Test void historicalCaseRemainsProtectedAfterTheActiveMarkerWasReleased()throws Exception{
        Path state=directory.resolve("l02-connected-state.json");
        assertThatCode(()->NativePostgresTestDatabase.assertNoHistoricalL02Fixture(state)).doesNotThrowAnyException();
        Files.writeString(state,"{\"result\":\"PASS\",\"TEST_CLEANUP_RELEASED\":true}");
        assertThatThrownBy(()->NativePostgresTestDatabase.assertNoHistoricalL02Fixture(state)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("creation counters must be preserved");
        assertThat(Files.readString(state)).contains("PASS");
    }
}
