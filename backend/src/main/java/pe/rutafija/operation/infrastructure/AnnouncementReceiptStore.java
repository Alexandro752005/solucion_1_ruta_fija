package pe.rutafija.operation.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL UPSERT access for per-user mobile announcement receipts. */
@Repository
public class AnnouncementReceiptStore {

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementReceiptStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordDelivery(UUID announcementId, UUID userId, Instant receivedAt) {
        jdbcTemplate.update("""
                insert into announcement_receipt (announcement_id, user_id, delivered_at, read_at)
                values (?, ?, ?, null)
                on conflict (announcement_id, user_id) do nothing
                """, announcementId, userId, Timestamp.from(receivedAt));
    }

    public Receipt recordRead(UUID announcementId, UUID userId, Instant receivedAt) {
        jdbcTemplate.update("""
                insert into announcement_receipt (announcement_id, user_id, delivered_at, read_at)
                values (?, ?, ?, ?)
                on conflict (announcement_id, user_id) do update
                   set read_at = coalesce(announcement_receipt.read_at, excluded.read_at)
                """, announcementId, userId, Timestamp.from(receivedAt), Timestamp.from(receivedAt));
        return find(announcementId, userId).orElseThrow();
    }

    public Optional<Receipt> find(UUID announcementId, UUID userId) {
        return jdbcTemplate.query("""
                        select delivered_at, read_at
                          from announcement_receipt
                         where announcement_id = ? and user_id = ?
                        """,
                statement -> {
                    statement.setObject(1, announcementId);
                    statement.setObject(2, userId);
                },
                resultSet -> resultSet.next()
                        ? Optional.of(new Receipt(
                                resultSet.getTimestamp("delivered_at").toInstant(),
                                resultSet.getTimestamp("read_at") == null
                                        ? null
                                        : resultSet.getTimestamp("read_at").toInstant()
                        ))
                        : Optional.empty()
        );
    }

    public record Receipt(Instant deliveredAt, Instant readAt) {
    }
}
