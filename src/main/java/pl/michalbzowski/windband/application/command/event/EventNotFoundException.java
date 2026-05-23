package pl.michalbzowski.windband.application.command.event;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(Long id) {
        super("Event not found: " + id);
    }
}
