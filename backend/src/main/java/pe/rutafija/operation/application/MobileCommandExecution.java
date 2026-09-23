package pe.rutafija.operation.application;

/** Result plus whether a previous receipt made the command a no-op replay. */
public record MobileCommandExecution<T>(T value, boolean replayed) {
}
