-- V24__add_consent_tables.sql (PostgreSQL)
CREATE TABLE member_consents (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    consent_type VARCHAR(30) NOT NULL,
    granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP NULL,
    CONSTRAINT uk_member_consent_type UNIQUE (member_id, consent_type),
    CONSTRAINT fk_member_consent_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

CREATE TABLE member_consent_tokens (
    id UUID PRIMARY KEY,
    token UUID NOT NULL UNIQUE,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    CONSTRAINT fk_member_consent_token_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);