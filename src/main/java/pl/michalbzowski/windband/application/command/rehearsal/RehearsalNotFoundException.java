package pl.michalbzowski.windband.application.command.rehearsal;

public class RehearsalNotFoundException extends RuntimeException {
    public RehearsalNotFoundException(Long id) {
        super("Rehearsal not found: " + id);
    }
}
