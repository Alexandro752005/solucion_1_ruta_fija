package pe.rutafija.routing;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import pe.rutafija.routing.application.TerminalCorroborationPolicy;
import pe.rutafija.routing.application.TerminalCorroborationPolicy.Point;
import static org.assertj.core.api.Assertions.assertThat;
class TerminalCorroborationPolicyTest {
    final Instant now=Instant.parse("2026-10-19T12:00:00Z");
    @Test void exactTimeAndAccuracyLimitsAreInclusive(){assertThat(TerminalCorroborationPolicy.result(-6.77,-79.84,new Point(-6.77,-79.84,100,now.minusSeconds(120)),now)).isEqualTo("CORROBORATED");}
    @Test void missingPointOrTerminalNeverCorroborates(){assertThat(TerminalCorroborationPolicy.result(-6.77,-79.84,null,now)).isEqualTo("NOT_CORROBORATED_NO_POINT");assertThat(TerminalCorroborationPolicy.result(null,null,new Point(-6.77,-79.84,1,now),now)).isEqualTo("NOT_CORROBORATED_TERMINAL_UNCONFIGURED");}
    @Test void staleFutureAndPoorAccuracyRemainUncorroborated(){for(var p:new Point[]{new Point(0,0,100.01,now),new Point(0,0,1,now.minusSeconds(120).minusNanos(1)),new Point(0,0,1,now.plusNanos(1)),new Point(0,0,Double.NaN,now)})assertThat(TerminalCorroborationPolicy.result(0.0,0.0,p,now)).isEqualTo("NOT_CORROBORATED_STALE_OR_IMPRECISE");}
    @Test void pointsInsideAndOutsideRadiusAreDistinguished(){double degree=Math.toDegrees(1.0/6_371_000);assertThat(TerminalCorroborationPolicy.result(0.0,0.0,new Point(199.99*degree,0,1,now),now)).isEqualTo("CORROBORATED");assertThat(TerminalCorroborationPolicy.result(0.0,0.0,new Point(200.01*degree,0,1,now),now)).isEqualTo("DISCREPANCY");}
}
