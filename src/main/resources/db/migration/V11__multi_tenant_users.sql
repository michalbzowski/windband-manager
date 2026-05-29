-- V10: Create app_users table (multi-tenant users independent of admin hardcoded user)
CREATE TABLE app_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64) NOT NULL,
    email           VARCHAR(128) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMP
);

CREATE UNIQUE INDEX idx_app_users_username ON app_users (username);
CREATE UNIQUE INDEX idx_app_users_email ON app_users (email);

-- V10: Create user_team_roles table (many-to-many between users and bands/teams)
CREATE TABLE user_team_roles (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    team_id               BIGINT NOT NULL REFERENCES bands(id) ON DELETE CASCADE,
    role                  VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    assigned_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    invitation_token      VARCHAR(64),
    invitation_accepted   BOOLEAN NOT NULL DEFAULT FALSE,
    invitation_accepted_at TIMESTAMP,
    CONSTRAINT uq_user_team UNIQUE (user_id, team_id),
    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'MEMBER'))
);

CREATE INDEX idx_user_team_roles_user_id ON user_team_roles (user_id);
CREATE INDEX idx_user_team_roles_team_id ON user_team_roles (team_id);
CREATE INDEX idx_user_team_roles_invitation_token ON user_team_roles (invitation_token) WHERE invitation_token IS NOT NULL;

-- V11: Ensure bands table has a slug for URL-friendly identification
ALTER TABLE bands ADD COLUMN IF NOT EXISTS slug VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bands_slug ON bands (slug);

-- V11: Seed default band with slug (for backward compat with existing code that uses band id=1)
UPDATE bands SET slug = 'default-band' WHERE slug IS NULL;
ALTER TABLE bands ALTER COLUMN slug SET NOT NULL;

-- V11: Seed default admin user for existing installations
-- Password: 'admin' (BCrypt hash)
INSERT INTO app_users (username, email, password_hash, active, email_verified, created_at)
SELECT 'admin', 'admin@windband.local',
       '$2a$10$87mXiUF7Umvv6j33YsG2l.FyRtvcbQOLDNaeOj4MFSVEAH5uxR2Re', true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'admin');

-- V11: Link admin user to default band
INSERT INTO user_team_roles (user_id, team_id, role, assigned_at, invitation_accepted, invitation_accepted_at)
SELECT 1, 1, 'ADMIN', NOW(), true, NOW()
WHERE EXISTS (SELECT 1 FROM bands WHERE id = 1)
  AND EXISTS (SELECT 1 FROM app_users WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM user_team_roles WHERE user_id = 1 AND team_id = 1);
