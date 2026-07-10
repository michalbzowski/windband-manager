package pl.michalbzowski.windband.application.security;

/**
 * Abstraction for the current authenticated user.
 * Implemented by WindbandOidcUser in adapter layer.
 */
public interface CurrentUser {
    String getName();
}