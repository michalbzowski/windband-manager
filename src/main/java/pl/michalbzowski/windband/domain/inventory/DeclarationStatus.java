package pl.michalbzowski.windband.domain.inventory;

/**
 * Status of a private possession declaration.
 */
public enum DeclarationStatus {
    /**
     * Declaration is active and valid.
     */
    ACTIVE("Aktywna"),

    /**
     * Declaration has expired (validUntil date passed).
     */
    EXPIRED("Wygaśnięta"),

    /**
     * Declaration was revoked by member or admin.
     */
    REVOKED("Anulowana"),

    /**
     * Declaration pending verification.
     */
    PENDING_VERIFICATION("Oczekuje weryfikacji");

    private final String displayName;

    DeclarationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}