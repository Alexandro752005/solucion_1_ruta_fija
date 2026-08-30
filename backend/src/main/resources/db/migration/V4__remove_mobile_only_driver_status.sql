-- El CRM web no dispone de telemetria ni aplicacion movil. DESCONECTADO era
-- un estado reservado para ese canal y no forma parte del flujo operativo web.
UPDATE driver
   SET availability_status = 'NO_DISPONIBLE'
 WHERE availability_status = 'DESCONECTADO';

ALTER TABLE driver DROP CONSTRAINT ck_driver_availability_status;

ALTER TABLE driver
    ADD CONSTRAINT ck_driver_availability_status CHECK (
        availability_status IN (
            'DISPONIBLE',
            'RESERVADO',
            'EN_SERVICIO',
            'DESCANSO',
            'NO_DISPONIBLE'
        )
    );
