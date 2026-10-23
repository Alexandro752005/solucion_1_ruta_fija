package pe.rutafija.operation.api.dto.mobile;

/** Client-reported OS state, not hardware attestation. Functional consent is checked in the database. */
public record MobileGpsStartProof(Boolean permissionGranted, Boolean locationServiceEnabled, Boolean foreground) {
    public boolean permitsStart() {
        return Boolean.TRUE.equals(permissionGranted) && Boolean.TRUE.equals(locationServiceEnabled)
                && Boolean.TRUE.equals(foreground);
    }
    public String canonical() { return permissionGranted+"|"+locationServiceEnabled+"|"+foreground; }
}
