package pe.rutafija.operation.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Only stores the current point; PostgreSQL PK(driver_id) prevents history. */
@Repository
public class DriverCurrentLocationStore {

    private final JdbcTemplate jdbcTemplate;

    public DriverCurrentLocationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(
            UUID driverId,
            UUID organizationId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyM,
            Instant capturedAt,
            Instant receivedAt,
            Instant expiresAt
    ) {
        jdbcTemplate.update("""
                insert into driver_current_location (
                    driver_id, organization_id, latitude, longitude, accuracy_m,
                    captured_at, received_at, expires_at, source
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'MOBILE_APP')
                on conflict (driver_id) do update set
                    organization_id = excluded.organization_id,
                    latitude = excluded.latitude,
                    longitude = excluded.longitude,
                    accuracy_m = excluded.accuracy_m,
                    captured_at = excluded.captured_at,
                    received_at = excluded.received_at,
                    expires_at = excluded.expires_at,
                    source = 'MOBILE_APP'
                """,
                driverId, organizationId, latitude, longitude, accuracyM,
                Timestamp.from(capturedAt), Timestamp.from(receivedAt), Timestamp.from(expiresAt));
    }

    public void deleteByDriverId(UUID driverId) {
        jdbcTemplate.update("delete from driver_current_location where driver_id = ?", driverId);
    }

    public int deleteExpiredBefore(Instant now) {
        return jdbcTemplate.update("delete from driver_current_location where expires_at <= ?", Timestamp.from(now));
    }
}
