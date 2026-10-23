package pl.michalbzowski.windband.adapter.out.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import pl.michalbzowski.windband.application.security.SecretCipher;

/**
 * AES-256-GCM z kluczem głównym z env.
 *
 * <p>Akceptowane formaty klucza głównego:
 * <ol>
 *   <li>Base64 dekodujący się do dokładnie 32 bajtów (produkcja:
 *       {@code openssl rand -base64 32});</li>
 *   <li>surowa fraza UTF-8 o długości dokładnie 32 bajtów (wygoda dev/test —
 *       czytelne frazy nie są flagowane przez skanery sekretów w CI).</li>
 * </ol>
 *
 * <p>Format zapisu: Base64(IV[12] || ciphertext || tag[16]); losowy IV przy
 * każdym szyfrowaniu, więc ta sama jawna wartość daje za każdym razem inny
 * ciphertext (odporność na analizę częstości i ataki replays).
 *
 * <p>Brak klucza głównego = szyfr wyłączony ({@link #isEnabled()} false),
 * a encrypt/decrypt rzucają czytelny wyjątek (fail-closed). Aplikacja startuje
 * normalnie — skarbiec po prostu nie działa, dopóki operator nie ustawi
 * {@code LLM_KEYS_MASTER_KEY}. Błędny klucz (ani Base64 32 B, ani surowe 32 B)
 * jest natomiast odrzucany od razu przy starcie: to błąd konfiguracji, nie runtime.
 */
public final class AesGcmSecretCipher implements SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private static final String NOT_DECRYPTABLE =
            "Nie udało się odszyfrować klucza — nieprawidłowy klucz główny lub uszkodzone dane.";

    private final SecretKeySpec key; // null = skarbiec wyłączony
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            this.key = null;
            return;
        }
        this.key = new SecretKeySpec(deriveKeyBytes(masterKey.trim()), "AES");
    }

    /** Base64(32 B) albo surowa fraza UTF-8(32 B) — cokolwiek inne to błąd konfiguracji. */
    private static byte[] deriveKeyBytes(String masterKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length == KEY_BYTES) {
                return decoded;
            }
        } catch (IllegalArgumentException e) {
            // nie-Base64 — sprawdzamy jeszcze ścieżkę surowej frazy poniżej
        }
        byte[] raw = masterKey.getBytes(StandardCharsets.UTF_8);
        if (raw.length == KEY_BYTES) {
            return raw;
        }
        throw new IllegalStateException(
                "LLM_KEYS_MASTER_KEY musi być Base64 o długości 32 bajtów (AES-256) "
                        + "albo surową frazą UTF-8 o długości dokładnie 32 bajtów; "
                        + "surowa fraza ma " + raw.length + " bajtów.");
    }

    @Override
    public boolean isEnabled() {
        return key != null;
    }

    @Override
    public String encrypt(String plaintext) {
        requireEnabled();
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Nie udało się zaszyfrować klucza.", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        requireEnabled();
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("ciphertext required");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(ciphertext.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(NOT_DECRYPTABLE, e);
        }
        if (raw.length <= IV_BYTES) {
            throw new IllegalStateException(NOT_DECRYPTABLE);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            byte[] pt = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // w tym AEADBadTagException: zły klucz główny albo uszkodzone dane
            throw new IllegalStateException(NOT_DECRYPTABLE, e);
        }
    }

    private void requireEnabled() {
        if (key == null) {
            throw new IllegalStateException("Skarbiec kluczy jest wyłączony: ustaw LLM_KEYS_MASTER_KEY "
                    + "(Base64, 32 bajty) w środowisku serwera.");
        }
    }
}
