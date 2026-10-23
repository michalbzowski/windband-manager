package pl.michalbzowski.windband.adapter.out.crypto;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kontrakt AES-256-GCM dla skarbca kluczy LLM: round-trip, losowy IV,
 * fail-closed przy złym kluczu głównym / uszkodzonych danych / braku klucza.
 */
class AesGcmSecretCipherTest {

    private static String newMasterKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    @DisplayName("encrypt → decrypt zwraca jawną wartość (round-trip, UTF-8)")
    void roundTrip() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(newMasterKey());
        assertThat(cipher.isEnabled()).isTrue();

        String secret = "tajny-klucz-z-polskimi-ąśćź-1234567890";

        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    @DisplayName("losowy IV: ta sama jawna wartość daje różne ciphertexty")
    void ivIsRandomPerEncryption() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(newMasterKey());

        assertThat(cipher.encrypt("ten-sam-klucz")).isNotEqualTo(cipher.encrypt("ten-sam-klucz"));
    }

    @Test
    @DisplayName("zły klucz główny → IllegalStateException bez wycieku danych")
    void wrongMasterKeyFailsCleanly() {
        String ciphertext = new AesGcmSecretCipher(newMasterKey()).encrypt("secret-value");
        AesGcmSecretCipher other = new AesGcmSecretCipher(newMasterKey());

        assertThatThrownBy(() -> other.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("klucz główny");
    }

    @Test
    @DisplayName("uszkodzony ciphertext (flip bitu w tagu GCM) → IllegalStateException")
    void tamperedCiphertextFails() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(newMasterKey());
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("secret-value"));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("brak klucza głównego → isEnabled false, encrypt/decrypt fail-closed")
    void blankMasterKeyDisablesVault() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher("   ");

        assertThat(cipher.isEnabled()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_KEYS_MASTER_KEY");
        assertThatThrownBy(() -> cipher.decrypt("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_KEYS_MASTER_KEY");
    }

    @Test
    @DisplayName("surowa fraza UTF-8 o długości 32 bajtów jest akceptowana (ścieżka dev/test)")
    void rawAsciiKeyOf32BytesIsAccepted() {
        // Czytelna fraza zamiast losowego Base64 — skanery sekretów w CI nie flagują fixture'ów.
        AesGcmSecretCipher cipher = new AesGcmSecretCipher("windband-test-master-key-32bytes");
        assertThat(cipher.isEnabled()).isTrue();
        assertThat(cipher.decrypt(cipher.encrypt("sekret-z-ąśćź"))).isEqualTo("sekret-z-ąśćź");
    }

    @Test
    @DisplayName("błędny klucz główny (ani Base64 32 B, ani surowe 32 B) odrzucony przy konstrukcji")
    void invalidMasterKeyRejectedAtConstruction() {
        assertThatThrownBy(() -> new AesGcmSecretCipher("to-nie-jest-base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");

        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmSecretCipher(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
