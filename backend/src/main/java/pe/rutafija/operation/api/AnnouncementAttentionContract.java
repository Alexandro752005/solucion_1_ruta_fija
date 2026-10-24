package pe.rutafija.operation.api;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class AnnouncementAttentionContract {
    private AnnouncementAttentionContract(){}
    public record Withdrawal(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,@NotBlank @Size(max=300) String reason){}
    public record Recipient(UUID userId,UUID driverId,String label,long version,String accessStatus,Instant assignedAt,Instant deliveredAt,Instant readAt,Instant withdrawnAt,UUID withdrawalKey,UUID withdrawnBy){}
    public record Attention(UUID announcementId,boolean requireReadAck,long recipients,long pending,List<Recipient> items){}
    public record Publication(UUID operationKey,UUID announcementId,Instant createdAt){}
}
