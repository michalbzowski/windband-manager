-- V49: skarbiec kluczy API użytkownika do serwisów LLM (tokeny OpenRouter
-- używane przy chmurowej analizie partytur).
--
-- Jawna wartość klucza NIGDY nie trafia do bazy — kolumna ciphertext przechowuje
-- Base64(IV[12] || ciphertext || tag[16]) z AES-256-GCM; klucz główny żyje
-- wyłącznie w środowisku serwera (LLM_KEYS_MASTER_KEY).
--
-- Jeden klucz na (użytkownik, dostawca): ponowny zapis nadpisuje wiersz.

CREATE TABLE IF NOT EXISTS llm_api_keys (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT NOT NULL REFERENCES app_users(id),
    provider    VARCHAR(30) NOT NULL DEFAULT 'OPENROUTER'
                    CHECK (provider IN ('OPENROUTER')),
    label       VARCHAR(100) NOT NULL,
    key_hint    VARCHAR(12) NOT NULL,
    ciphertext  VARCHAR(1024) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_llm_api_keys_owner_provider UNIQUE (owner_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_llm_api_keys_owner ON llm_api_keys(owner_id);

COMMENT ON TABLE llm_api_keys IS 'Klucze API użytkownika do serwisów LLM (AES-GCM, klucz główny z env)';
