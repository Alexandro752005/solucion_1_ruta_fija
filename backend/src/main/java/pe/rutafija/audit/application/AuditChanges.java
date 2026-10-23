package pe.rutafija.audit.application;

import java.util.*;

/** Construct differences without ever putting personal or free text into an audit map. */
public final class AuditChanges {
    private AuditChanges() { }
    public static List<Map<String,Object>> fields(Object... triples){
        if(triples.length%3!=0)throw new IllegalArgumentException("AUDIT_CHANGE_TRIPLES_REQUIRED");
        var values=new ArrayList<Map<String,Object>>();
        for(int i=0;i<triples.length;i+=3)if(!Objects.equals(triples[i+1],triples[i+2]))
            values.add(Map.of("field",triples[i].toString(),"before",value(triples[i+1]),"after",value(triples[i+2])));
        return List.copyOf(values);
    }
    public static Map<String,Object> masked(String field,Object before,Object after){
        return Objects.equals(before,after)?Map.of():Map.of("field",field,"before",before==null?"NOT_SET":"REDACTED","after",after==null?"NOT_SET":"REDACTED");
    }
    @SafeVarargs public static List<Map<String,Object>> combine(List<Map<String,Object>> base,Map<String,Object>... extra){
        var result=new ArrayList<>(base);for(var value:extra)if(!value.isEmpty())result.add(value);return List.copyOf(result);
    }
    private static Object value(Object v){
        if(v==null)return "NOT_SET";
        if(v instanceof UUID||v instanceof Enum<?>||v instanceof java.time.Instant||v instanceof Number||v instanceof Boolean)return v;
        throw new IllegalArgumentException("AUDIT_USE_MASKED_FOR_FREE_TEXT");
    }
}
