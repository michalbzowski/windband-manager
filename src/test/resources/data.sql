-- Default test data for H2 in-memory database
-- Band id=1 "Test Band" (auto-generated id by H2)

INSERT INTO bands (name, slug, description, created_at) VALUES ('Test Band', 'test-band', 'Default band for tests', CURRENT_DATE);

-- Second band for team isolation tests
INSERT INTO bands (name, slug, description, created_at) VALUES ('Other Band', 'other-band', 'Second band for isolation tests', CURRENT_DATE);

-- Create default admin user for tests (password: admin)
INSERT INTO app_users (id, username, email, password_hash, active, system_admin, email_verified, created_at)
VALUES (1, 'admin', 'admin@test.com', '$2a$10$87mXiUF7Umvv6j33YsG2l.FyRtvcbQOLDNaeOj4MFSVEAH5uxR2Re', true, false, true, CURRENT_TIMESTAMP);

-- Second user who belongs to BOTH bands (for team isolation testing)
INSERT INTO app_users (id, username, email, password_hash, active, system_admin, email_verified, created_at)
VALUES (2, 'multiuser', 'multi@test.com', '$2a$10$87mXiUF7Umvv6j33YsG2l.FyRtvcbQOLDNaeOj4MFSVEAH5uxR2Re', true, false, true, CURRENT_TIMESTAMP);

-- Link admin user to test band only
INSERT INTO user_team_roles (user_id, team_id, role, assigned_at, invitation_accepted, invitation_accepted_at)
VALUES (1, 1, 'ADMIN', CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP);

-- Link multiuser to BOTH bands
INSERT INTO user_team_roles (user_id, team_id, role, assigned_at, invitation_accepted, invitation_accepted_at)
VALUES (2, 1, 'MEMBER', CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP);
INSERT INTO user_team_roles (user_id, team_id, role, assigned_at, invitation_accepted, invitation_accepted_at)
VALUES (2, 2, 'ADMIN', CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP);

-- Members for Test Band (band_id=1)
INSERT INTO members (first_name, last_name, date_of_birth, email, phone, active, joined_date, email_consent_given, band_id)
VALUES ('Jan', 'Kowalski', '1990-05-15', 'jan@test.com', '123456789', true, CURRENT_DATE, false, 1);
INSERT INTO members (first_name, last_name, date_of_birth, email, phone, active, joined_date, email_consent_given, band_id)
VALUES ('Anna', 'Nowak', '1985-03-20', 'anna@test.com', '987654321', true, CURRENT_DATE, false, 1);

-- Members for Other Band (band_id=2) — must NOT appear in Test Band views
INSERT INTO members (first_name, last_name, date_of_birth, email, phone, active, joined_date, email_consent_given, band_id)
VALUES ('Piotr', 'Zalewski', '1992-07-10', 'piotr@other.com', '111222333', true, CURRENT_DATE, false, 2);
INSERT INTO members (first_name, last_name, date_of_birth, email, phone, active, joined_date, email_consent_given, band_id)
VALUES ('Maria', 'Wojcik', '1988-11-25', 'maria@other.com', '444555666', true, CURRENT_DATE, false, 2);

-- Groups for Test Band (band_id=1)
INSERT INTO member_groups (name, description, band_id) VALUES ('Trąbki', 'Trębacze', 1);
INSERT INTO member_groups (name, description, band_id) VALUES ('Perkusja', 'Perkusyści', 1);

-- Groups for Other Band (band_id=2) — must NOT appear in Test Band views
INSERT INTO member_groups (name, description, band_id) VALUES ('Saksofony', 'Saksofoniści', 2);

-- Inventory items for Test Band
INSERT INTO uniform_items (name, description, ownership_status, lifecycle_status, band_id)
VALUES ('Bluza Test', 'Bluza orkiestrowa', 'OWNED', 'AVAILABLE', 1);
INSERT INTO instrument_items (name, brand, serial_number, description, ownership_status, lifecycle_status, band_id)
VALUES ('Trąbka Test', 'Yamaha', 'SN001', 'Trąbka Bb', 'OWNED', 'AVAILABLE', 1);
INSERT INTO award_items (name, description, band_id, date_awarded)
VALUES ('Medal Test', 'Medal za zasługi', 1, CURRENT_DATE);

-- Inventory items for Other Band — must NOT appear in Test Band views
INSERT INTO uniform_items (name, description, ownership_status, lifecycle_status, band_id)
VALUES ('Bluza Other', 'Bluza innego zespołu', 'OWNED', 'AVAILABLE', 2);
INSERT INTO instrument_items (name, brand, serial_number, description, ownership_status, lifecycle_status, band_id)
VALUES ('Saksofon Other', 'Selmer', 'SN002', 'Saksofon alt', 'OWNED', 'AVAILABLE', 2);
INSERT INTO award_items (name, description, band_id, date_awarded)
VALUES ('Medal Other', 'Medal innego zespołu', 2, CURRENT_DATE);

-- Instruments (global catalog — not band-specific)
INSERT INTO instruments (name, description, sort_priority) VALUES ('Trąbka', 'Trąbka Bb', 1);
INSERT INTO instruments (name, description, sort_priority) VALUES ('Bęben', 'Bęben wielki', 2);
INSERT INTO instruments (name, description, sort_priority) VALUES ('Saksofon', 'Saksofon alt', 3);

-- Member instruments (Jan plays Trąbka, Anna plays Bęben)
INSERT INTO member_instruments (member_id, instrument_id, is_primary)
VALUES (1, 1, true);
INSERT INTO member_instruments (member_id, instrument_id, is_primary)
VALUES (2, 2, true);
