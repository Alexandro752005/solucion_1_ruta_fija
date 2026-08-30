CREATE TABLE transport_group (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_transport_group_organization_name_lower
    ON transport_group (organization_id, LOWER(name));
CREATE INDEX ix_transport_group_organization_active
    ON transport_group (organization_id, active);

CREATE TABLE group_coordinator (
    group_id UUID NOT NULL REFERENCES transport_group(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by UUID NOT NULL REFERENCES app_user(id),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX ix_group_coordinator_user ON group_coordinator (user_id);

CREATE TABLE driver (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    user_id UUID UNIQUE REFERENCES app_user(id),
    group_id UUID NOT NULL REFERENCES transport_group(id),
    full_name VARCHAR(160) NOT NULL,
    phone VARCHAR(30),
    document_type VARCHAR(20) NOT NULL,
    document_number VARCHAR(30) NOT NULL,
    license_number VARCHAR(40),
    availability_status VARCHAR(25) NOT NULL DEFAULT 'NO_DISPONIBLE',
    location_consent BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_driver_availability_status CHECK (
        availability_status IN (
            'DISPONIBLE',
            'RESERVADO',
            'EN_SERVICIO',
            'DESCANSO',
            'NO_DISPONIBLE',
            'DESCONECTADO'
        )
    )
);

CREATE UNIQUE INDEX uq_driver_organization_document_number_lower
    ON driver (organization_id, LOWER(document_number));
CREATE INDEX ix_driver_organization_group_status_active
    ON driver (organization_id, group_id, availability_status, active);

CREATE TABLE vehicle (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    plate VARCHAR(15) NOT NULL,
    brand VARCHAR(60),
    model VARCHAR(60),
    year SMALLINT,
    color VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_vehicle_status CHECK (
        status IN ('DISPONIBLE', 'EN_SERVICIO', 'MANTENIMIENTO', 'INACTIVO')
    ),
    CONSTRAINT ck_vehicle_year CHECK (year IS NULL OR year BETWEEN 1900 AND 2100)
);

CREATE UNIQUE INDEX uq_vehicle_organization_plate_lower
    ON vehicle (organization_id, LOWER(plate));
CREATE INDEX ix_vehicle_organization_status_active
    ON vehicle (organization_id, status, active);

CREATE TABLE driver_vehicle_link (
    driver_id UUID NOT NULL REFERENCES driver(id),
    vehicle_id UUID NOT NULL REFERENCES vehicle(id),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    linked_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (driver_id, vehicle_id)
);

CREATE UNIQUE INDEX uq_driver_vehicle_one_active_primary
    ON driver_vehicle_link (driver_id)
    WHERE active AND is_primary;
CREATE INDEX ix_driver_vehicle_link_vehicle_active
    ON driver_vehicle_link (vehicle_id, active);
