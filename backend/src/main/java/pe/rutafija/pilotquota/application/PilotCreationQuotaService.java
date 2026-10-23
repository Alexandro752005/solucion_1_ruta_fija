package pe.rutafija.pilotquota.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

/** Historical creation slots. The database trigger is the authoritative writer. */
@Service
public class PilotCreationQuotaService {
    private final JdbcTemplate jdbc;

    public PilotCreationQuotaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void requireAvailable(UserRole role) {
        int key = switch (role) { case SUPER_ADMIN -> 1; case ADMIN -> 2; case CONDUCTOR -> 3; };
        jdbc.query("select pg_advisory_xact_lock(751844, ?)", rs -> { }, key);
        if (historicalCreations(role) >= limit(role)) {
            throw new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "Se agotaron las creaciones históricas de " + role.name() + " autorizadas para el piloto");
        }
    }

    @Transactional(readOnly = true)
    public long historicalCreations(UserRole role) {
        Long count = jdbc.queryForObject("select count(*) from pilot_account_creation where role = ?",
                Long.class, role.name());
        return count == null ? 0 : count;
    }

    public int limit(UserRole role) {
        return switch (role) { case SUPER_ADMIN -> 1; case ADMIN -> 4; case CONDUCTOR -> 20; };
    }
}
