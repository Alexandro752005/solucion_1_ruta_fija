package pe.rutafija.operation.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** PostgreSQL transaction-scoped lock for a globally unique mobile event id. */
@Repository
public class PostgresMobileEventLock {

    private final JdbcTemplate jdbcTemplate;

    public PostgresMobileEventLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(UUID eventId) {
        jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtext(cast(? as text)))",
                statement -> statement.setObject(1, eventId),
                resultSet -> null
        );
    }
}
