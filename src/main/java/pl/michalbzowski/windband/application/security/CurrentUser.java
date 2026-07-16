package pl.michalbzowski.windband.application.security;

/**
 * Abstraction for the current authenticated user.
 * Implemented by WindbandOidcUser in adapter layer.
 */
public interface CurrentUser {
    String getName();

    /**
     * Human-readable display name (e.g. first + last name).
     *
     * <p>Defaults to {@link #getName()} so existing implementations and test mocks
     * that only stub {@code getName()} keep working. Concrete adapters (e.g.
     * WindbandOidcUser) override this to return a proper display name from OIDC claims.
     */
    default String getDisplayName() {
        return getName();
    }
}
