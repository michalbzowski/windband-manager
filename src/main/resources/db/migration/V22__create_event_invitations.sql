-- V22: Create event_invitations table for magic link invitations
-- Each row links a BandEvent + Member with a unique token for public response

CREATE TABLE IF NOT EXISTS event_invitations (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES band_events(id) ON DELETE CASCADE,
    member_id       BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    token           VARCHAR(36) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'NOT_SENT',
    sent_at         TIMESTAMP,
    responded_at    TIMESTAMP,
    preferred_channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_event_member UNIQUE (event_id, member_id),
    CONSTRAINT uq_invitation_token UNIQUE (token)
);

CREATE INDEX idx_event_invitations_event_id ON event_invitations (event_id);
CREATE INDEX idx_event_invitations_member_id ON event_invitations (member_id);
CREATE INDEX idx_event_invitations_token ON event_invitations (token);
