package pe.rutafija.audit.application;

import org.springframework.stereotype.Component;
import pe.rutafija.shared.exception.ErrorCode;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Positive metadata projection shared by new writes and legacy reads. No free text. */
@Component
public final class AuditMetadataPolicy {
    private static final Pattern PRIVATE=Pattern.compile("password|secret|token|cookie|authorization|email|phone|contact|fullname|firstname|lastname|displayname|dni|documentnumber|licensenumber|latitude|longitude|coordinates|location|credential|private|signature|otp",Pattern.CASE_INSENSITIVE);
    private static final Set<String> FIELDS=Set.of("status","previousStatus","newStatus","before","after","result","outcome","auditOutcome","reason","reasonCode","field","changes","fields","details","context","mode","scope","channel","kind","source","action","operationKey","role","previousRole","newRole","version","previousVersion","expectedVersion","resultingVersion","count","total","syntheticOnly","resourcesReleased","operationalEnabled","enabled","active","requireReadAck","evidenceMode","evidenceReference","evidenceRequired","documentVersion","contentSha256","payloadSha256","publicKeySha256","publicKeyFingerprint","keyFingerprint","algorithm","evidenceType","decision","code","state","previous","current");
    private static final Set<String> HASH_FIELDS=Set.of("contentSha256","payloadSha256","publicKeySha256","publicKeyFingerprint","keyFingerprint");
    private static final Set<String> FREE=Set.of("note","description","diagnosis","actions","fullName","name","evidenceExplanation");
    public Map<String,Object> sanitize(Map<String,?> source){return project(source==null?Map.of():source,0);}
    private Map<String,Object> project(Map<String,?> source,int depth){
        if(depth>8)return Map.of();var result=new LinkedHashMap<String,Object>();
        boolean personalChange=source.get("field") instanceof String f&&(PRIVATE.matcher(f).find()||f.toLowerCase(Locale.ROOT).contains("name"));
        source.entrySet().stream().filter(e->e.getKey()!=null).sorted(Map.Entry.comparingByKey()).limit(64).forEach(entry->{
            String key=entry.getKey();if(PRIVATE.matcher(key).find()||FREE.contains(key)||!allowed(key))return;
            if(personalChange&&Set.of("before","after","previous","current").contains(key)){result.put(key,"[REDACTADO]");return;}
            Object safe=value(key,entry.getValue(),depth+1);if(safe!=null)result.put(key,safe);
        });return Collections.unmodifiableMap(result);
    }
    private boolean allowed(String key){return Set.of("module","method","httpStatus","revision").contains(key)||FIELDS.contains(key)||key.matches("[a-z][A-Za-z0-9]{0,55}(?:Id|Ids|Version|Count|At|Enabled|Required)");}
    private Object value(String key,Object raw,int depth){
        if(raw==null||depth>8)return null;
        if(raw instanceof UUID||raw instanceof Instant||raw instanceof Enum<?>)return raw.toString();
        if(raw instanceof Boolean)return raw;
        if(raw instanceof Number n){double d=n.doubleValue();return Double.isFinite(d)&&d==Math.rint(d)?n:null;}
        if(raw instanceof Map<?,?> map){var typed=new LinkedHashMap<String,Object>();map.forEach((k,v)->{if(k instanceof String s)typed.put(s,v);});return project(typed,depth);}
        if(raw instanceof Collection<?> list){var safe=new ArrayList<Object>();for(Object item:list.stream().limit(128).toList()){Object v=value(key,item,depth+1);if(v!=null)safe.add(v);}return List.copyOf(safe);}
        if(!(raw instanceof String text)||text.length()>150||text.indexOf('\n')>=0||text.indexOf('\r')>=0)return null;
        if(HASH_FIELDS.contains(key))return text.matches("[a-fA-F0-9]{64}")?text:null;
        try{UUID.fromString(text);return text;}catch(IllegalArgumentException ignored){}
        if(key.equals("evidenceReference"))return null;
        if(key.equals("reason")||key.equals("reasonCode")){try{return ErrorCode.valueOf(text).name();}catch(IllegalArgumentException ignored){return null;}}
        try{Instant.parse(text);return text;}catch(java.time.format.DateTimeParseException ignored){}
        if(key.equals("field"))return text.matches("[a-zA-Z][a-zA-Z0-9_.]{0,60}")?text:null;
        if(key.equals("operationKey")&&text.matches("[a-fA-F0-9-]{36}:[a-zA-Z0-9:_-]{1,80}"))return text;
        if(text.matches("[A-Z][A-Z0-9_.:-]{0,99}")&&!text.matches(".*[0-9]{6,}.*"))return text;
        if(key.equals("algorithm")&&Set.of("Ed25519","SHA-256").contains(text))return text;
        if(key.toLowerCase(Locale.ROOT).endsWith("version")&&text.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){0,3}"))return text;
        return null;
    }
}
