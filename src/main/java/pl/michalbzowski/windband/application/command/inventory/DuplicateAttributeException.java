package pl.michalbzowski.windband.application.command.inventory;

/**
 * Thrown when attempting to create an attribute definition that already exists
 * for the same band and name combination.
 */
public class DuplicateAttributeException extends RuntimeException {
    public DuplicateAttributeException(String message) {
        super(message);
    }
}
