package pe.rutafija.fleet.domain;

public enum VehicleStatus {
    DISPONIBLE,
    EN_SERVICIO,
    MANTENIMIENTO,
    INACTIVO;

    public boolean canBeSetAdministratively() {
        return this != EN_SERVICIO;
    }
}
