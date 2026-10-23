package pe.rutafija.operation.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Immutable service points with seven-day expiry, plus a segment-bound derived current snapshot. */
@Repository
@org.springframework.transaction.annotation.Transactional
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
        upsert(driverId,organizationId,null,latitude,longitude,accuracyM,capturedAt,receivedAt,expiresAt);
    }

    public void upsert(
            UUID driverId, UUID organizationId, UUID segmentId, BigDecimal latitude, BigDecimal longitude,
            BigDecimal accuracyM, Instant capturedAt, Instant receivedAt, Instant expiresAt
    ) {
        UUID pointId=segmentId==null?null:recordPoint(driverId,organizationId,segmentId,latitude,longitude,accuracyM,capturedAt,receivedAt);
        jdbcTemplate.update("""
                insert into driver_current_location (
                    driver_id, organization_id, latitude, longitude, accuracy_m,
                    captured_at, received_at, expires_at, execution_segment_id, gps_point_id, source
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MOBILE_APP')
                on conflict (driver_id) do update set
                    organization_id = excluded.organization_id,
                    latitude = excluded.latitude,
                    longitude = excluded.longitude,
                    accuracy_m = excluded.accuracy_m,
                    captured_at = excluded.captured_at,
                    received_at = excluded.received_at,
                    expires_at = excluded.expires_at,
                    execution_segment_id = excluded.execution_segment_id,
                    gps_point_id = excluded.gps_point_id,
                    source = 'MOBILE_APP'
                where driver_current_location.captured_at <= excluded.captured_at
                """,
                driverId, organizationId, latitude, longitude, accuracyM,
                Timestamp.from(capturedAt), Timestamp.from(receivedAt), Timestamp.from(expiresAt),segmentId,pointId);
    }

    private UUID recordPoint(UUID driver,UUID org,UUID segment,BigDecimal lat,BigDecimal lon,BigDecimal accuracy,Instant captured,Instant received) {
        UUID id=UUID.randomUUID();
        jdbcTemplate.update("""
            insert into service_gps_point(id,organization_id,driver_id,segment_id,latitude,longitude,accuracy_m,captured_at,received_at,expires_at)
            values(?,?,?,?,?,?,?,?,?,?) on conflict(segment_id,captured_at) do nothing
            """,id,org,driver,segment,lat,lon,accuracy,Timestamp.from(captured),Timestamp.from(received),Timestamp.from(captured.plus(java.time.Duration.ofDays(7))));
        var point=jdbcTemplate.queryForMap("select id,latitude,longitude,accuracy_m from service_gps_point where segment_id=? and captured_at=?",segment,Timestamp.from(captured));
        if(((BigDecimal)point.get("latitude")).compareTo(lat)!=0||((BigDecimal)point.get("longitude")).compareTo(lon)!=0||((BigDecimal)point.get("accuracy_m")).compareTo(accuracy)!=0)
            throw new pe.rutafija.shared.exception.ApplicationException(HttpStatus.CONFLICT,
                pe.rutafija.shared.exception.ErrorCode.LOCATION_INVALID_COORDINATES,"La misma captura ya conserva otro punto original; no se sobreescribe.");
        return (UUID)point.get("id");
    }

    public void deleteByDriverId(UUID driverId) {
        jdbcTemplate.update("delete from driver_current_location where driver_id = ?", driverId);
    }

    public int deleteExpiredBefore(Instant now) {
        return jdbcTemplate.update("delete from driver_current_location where expires_at <= ?", Timestamp.from(now));
    }
}
