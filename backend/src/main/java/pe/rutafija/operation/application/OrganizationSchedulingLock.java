package pe.rutafija.operation.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Acquire before driver/vehicle/assignment locks in tenant scheduling mutations. */
@Component
public class OrganizationSchedulingLock {
    private final JdbcTemplate jdbc;
    public OrganizationSchedulingLock(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void acquire(UUID organization){jdbc.queryForObject("select id from organization where id=? for no key update",UUID.class,organization);}
}
