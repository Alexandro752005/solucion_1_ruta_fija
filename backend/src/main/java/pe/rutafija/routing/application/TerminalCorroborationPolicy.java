package pe.rutafija.routing.application;

import java.time.*;

/** Q106 technical limits. This calculator does not activate tracking or collect a point. */
public final class TerminalCorroborationPolicy {
    public static final int RADIUS_METRES=200, MAX_AGE_SECONDS=120, MAX_ACCURACY_METRES=100;
    public record Point(double latitude,double longitude,double accuracyMetres,Instant capturedAt) { }
    private TerminalCorroborationPolicy(){ }
    public static String result(Double terminalLat,Double terminalLon,Point point,Instant now){
        if(terminalLat==null||terminalLon==null)return "NOT_CORROBORATED_TERMINAL_UNCONFIGURED";
        if(point==null)return "NOT_CORROBORATED_NO_POINT";
        if(!Double.isFinite(point.latitude())||!Double.isFinite(point.longitude())||!Double.isFinite(point.accuracyMetres())||Math.abs(point.latitude())>90||Math.abs(point.longitude())>180||point.accuracyMetres()<0||point.accuracyMetres()>MAX_ACCURACY_METRES||point.capturedAt()==null||point.capturedAt().isAfter(now)||point.capturedAt().isBefore(now.minusSeconds(MAX_AGE_SECONDS)))return "NOT_CORROBORATED_STALE_OR_IMPRECISE";
        return metres(terminalLat,terminalLon,point.latitude(),point.longitude())<=RADIUS_METRES?"CORROBORATED":"DISCREPANCY";
    }
    static double metres(double a,double b,double c,double d){double lat=Math.toRadians(c-a),lon=Math.toRadians(d-b);double h=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(lon/2),2);return 6_371_000*2*Math.atan2(Math.sqrt(Math.min(1,h)),Math.sqrt(Math.max(0,1-h)));}
}
