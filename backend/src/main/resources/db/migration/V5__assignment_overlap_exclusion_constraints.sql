-- La comprobación de aplicación evita conflictos comunes. Estas exclusiones son la barrera
-- definitiva frente a dos solicitudes concurrentes que intenten reservar el mismo recurso.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE assignment
    ADD CONSTRAINT ex_assignment_driver_schedule_no_overlap
    EXCLUDE USING gist (
        driver_id WITH =,
        tstzrange(scheduled_at, scheduled_end_at, '[)') WITH &&
    )
    WHERE (status IN ('SCHEDULED', 'EN_SERVICIO'));

ALTER TABLE assignment
    ADD CONSTRAINT ex_assignment_vehicle_schedule_no_overlap
    EXCLUDE USING gist (
        vehicle_id WITH =,
        tstzrange(scheduled_at, scheduled_end_at, '[)') WITH &&
    )
    WHERE (status IN ('SCHEDULED', 'EN_SERVICIO'));
