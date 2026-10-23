package pl.michalbzowski.windband.domain.llmkey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * Klucz API użytkownika do zewnętrznego serwisu LLM (skarbiec).
 *
 * <p>Jawna wartość klucza jest trzymana WYŁĄCZNIE jako {@code ciphertext}
 * (AES-256-GCM, Base64: IV[12] || ciphertext || tag[16]); nigdy nie jest
 * logowana ani zwracana przez API — UI widzi tylko {@code keyHint}
 * (ostatnie 4 znaki).
 *
 * <p>Unikalność (owner_id, provider): jeden klucz danego dostawcy na
 * użytkownika — ponowny zapis nadpisuje istniejący wiersz.
 */
@Entity
@Table(name = "llm_api_keys",
        uniqueConstraints = @UniqueConstraint(name = "uq_llm_api_keys_owner_provider",
                columnNames = {"owner_id", "provider"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LlmProvider provider;

    @Column(nullable = false, length = 100)
    private String label;

    /** Ostatnie 4 znaki jawnej wartości (np. „…ab12”) — bezpieczne do wyświetlenia. */
    @Column(name = "key_hint", nullable = false, length = 12)
    private String keyHint;

    /** Base64(IV || ciphertext || tag) — AES-GCM z kluczem głównym z env. */
    @Column(nullable = false, length = 1024)
    private String ciphertext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LlmApiKey create(Long ownerId, LlmProvider provider,
                                   String label, String keyHint, String ciphertext) {
        Objects.requireNonNull(ownerId, "ownerId required");
        Objects.requireNonNull(provider, "provider required");
        LlmApiKey key = new LlmApiKey();
        key.ownerId = ownerId;
        key.provider = provider;
        key.label = Objects.requireNonNull(label, "label required");
        key.keyHint = Objects.requireNonNull(keyHint, "keyHint required");
        key.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext required");
        key.createdAt = Instant.now();
        key.updatedAt = key.createdAt;
        return key;
    }

    /** Nadpisuje etykietę i zaszyfrowaną wartość istniejącego klucza (ten sam owner+provider). */
    public void replaceWith(String label, String keyHint, String ciphertext) {
        this.label = Objects.requireNonNull(label, "label required");
        this.keyHint = Objects.requireNonNull(keyHint, "keyHint required");
        this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext required");
        this.updatedAt = Instant.now();
    }
}
