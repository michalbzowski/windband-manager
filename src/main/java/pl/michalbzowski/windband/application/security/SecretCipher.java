package pl.michalbzowski.windband.application.security;

/**
 * Port szyfrowania/deszyfrowania sekretów aplikacji (np. kluczy API użytkowników).
 *
 * <p>Implementacja produkcyjna: AES-256-GCM z kluczem głównym z env
 * ({@code adapter.out.crypto.AesGcmSecretCipher}).
 */
public interface SecretCipher {

    /**
     * @return false, gdy serwer nie ma klucza głównego — skarbiec jest wyłączony,
     *         a {@link #encrypt}/{@link #decrypt} rzucają czytelny wyjątek (fail-closed).
     */
    boolean isEnabled();

    /**
     * @return trwały ciphertext (format zależy od implementacji); nigdy jawna wartość
     * @throws IllegalStateException gdy skarbiec jest wyłączony
     */
    String encrypt(String plaintext);

    /**
     * @throws IllegalStateException klucz główny nie pasuje lub dane są uszkodzone
     */
    String decrypt(String ciphertext);
}
