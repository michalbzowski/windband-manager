package pl.michalbzowski.windband.application.command.event;

public class ChannelException extends RuntimeException {
    public ChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}