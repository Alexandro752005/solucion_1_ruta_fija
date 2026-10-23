package pe.rutafija.support;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class L08PhysicalComplementPolicyTest {
    @Test void permitsOnlyRfc1918LiteralAddresses(){
        for(String ip:java.util.List.of("10.0.0.1","192.168.1.2","172.16.0.1","172.31.255.255"))assertThat(L08ConnectedTrial.privateIpv4(ip)).isTrue();
        for(String ip:java.util.Arrays.asList(null,"","localhost","127.0.0.1","0.0.0.0","8.8.8.8","172.15.0.1","172.32.0.1","192.168.1.256","192.168.1.2:4311","192.168.1.2/24"))assertThat(L08ConnectedTrial.privateIpv4(ip)).isFalse();
    }
    @Test void requiresTheFullApprovedInterval(){assertThat(pass(899,90,85,30,30,true,true)).isFalse();assertThat(pass(900,90,85,30,30,true,true)).isTrue();}
    @Test void respectsFivePercentagePointBoundary(){assertThat(pass(900,90,85,30,30,true,true)).isTrue();assertThat(pass(900,90,84,30,30,true,true)).isFalse();}
    @Test void refusesChargingAndMissingPersonalCondition(){assertThat(pass(900,90,89,30,30,false,true)).isFalse();assertThat(pass(900,90,89,30,30,true,false)).isFalse();}
    @Test void refusesRisingBatteryAndInvalidPercentages(){assertThat(pass(900,90,91,30,30,true,true)).isFalse();assertThat(pass(900,101,99,30,30,true,true)).isFalse();assertThat(pass(900,0,-1,30,30,true,true)).isFalse();}
    @Test void requiresActualPointsAndExistingProductFreshness(){assertThat(pass(900,90,89,0,30,true,true)).isFalse();assertThat(pass(900,90,89,30,121,true,true)).isFalse();assertThat(pass(900,90,89,30,120,true,true)).isTrue();}
    @Test void preservesOriginalProfileAndSeparateCaseReference(){assertThat(L08ConnectedTrial.RUN).isEqualTo("a999ef52-d87f-498d-9251-1581cbb72562");assertThat(L08ConnectedTrial.EXPECTED_CONDUCTOR_BEFORE).isEqualTo(6);assertThat(L08ConnectedTrial.COMPLEMENT_RUN).isNotEqualTo(L08ConnectedTrial.RUN);}
    private static boolean pass(long s,int a,int b,int p,long g,boolean n,boolean t){return L08ConnectedTrial.batteryAssessment(s,a,b,p,g,n,t);}
}
