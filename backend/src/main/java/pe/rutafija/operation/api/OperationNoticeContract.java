package pe.rutafija.operation.api;
import java.time.Instant;
import java.util.*;
public final class OperationNoticeContract {
    private OperationNoticeContract(){}
    public record Notice(UUID id,UUID userId,String eventType,Map<String,String> references,Instant createdAt,Instant readAt,String externalStatus,String externalReason){}
}
