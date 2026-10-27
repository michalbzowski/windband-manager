-- Pentest P2 (A01): tokeny zaproszeń bez expiary acceptInvitation
-- sprawdzała tylko status acceptance, nie datę. Nowe zaproszenia dostają
-- expires_at przypisany w TeamRegistrationService; NULL = istniejące
-- zaproszenia (grandfathering) bez terminu ważności.
ALTER TABLE user_team_roles ADD COLUMN invitation_expires_at TIMESTAMP;
