package pe.rutafija.backup;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Guard failures must not become successful scheduler jobs; no network, DB, file or process is started. */
class BackupCliGuardTest {
    @Test void backupWithoutExplicitConfigurationHasNonzeroExitStatus(){
        assertThat(TestDatabaseBackupCli.run(new String[0])).isEqualTo(1);
    }
    @Test void restoreWithoutExactAuthorizationHasNonzeroExitStatus(){
        assertThat(IsolatedTestRestoreCli.run(new String[0])).isEqualTo(1);
    }
}
