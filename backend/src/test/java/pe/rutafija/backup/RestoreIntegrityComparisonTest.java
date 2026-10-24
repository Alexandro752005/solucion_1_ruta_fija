package pe.rutafija.backup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class RestoreIntegrityComparisonTest {
 @Test void jdbcLongCountsMatchParsedArchiveIntegersWithoutIgnoringAnyField()throws Exception{
  var expected=new ObjectMapper().readTree("{\"table\":{\"rows\":2,\"rowSetSha256\":\"abc\"}}");
  assertThat(IsolatedTestRestoreCli.sameIntegrity(Map.of("table",Map.of("rows",2L,"rowSetSha256","abc")),expected)).isTrue();
 }
 @Test void changedCountHashMissingOrAdditionalObjectStillFails()throws Exception{
  var expected=new ObjectMapper().readTree("{\"table\":{\"rows\":2,\"rowSetSha256\":\"abc\"}}");
  assertThat(IsolatedTestRestoreCli.sameIntegrity(Map.of("table",Map.of("rows",3L,"rowSetSha256","abc")),expected)).isFalse();
  assertThat(IsolatedTestRestoreCli.sameIntegrity(Map.of("table",Map.of("rows",2L,"rowSetSha256","other")),expected)).isFalse();
  assertThat(IsolatedTestRestoreCli.sameIntegrity(Map.of(),expected)).isFalse();
  assertThat(IsolatedTestRestoreCli.sameIntegrity(Map.of("table",Map.of("rows",2L,"rowSetSha256","abc"),"extra",Map.of()),expected)).isFalse();
 }
}
