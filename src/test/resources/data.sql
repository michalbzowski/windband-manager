INSERT INTO bands (name, slug, description, created_at) VALUES ('Test Band', 'test-band', 'Default band for tests', CURRENT_DATE);

-- Create default admin user for tests (password: admin)
INSERT INTO app_users (id, username, email, password_hash, active, email_verified, created_at)
VALUES (1, 'admin', 'admin@test.com', '$2a$10$87mXiUF7Umvv6j33YsG2l.FyRtvcbQOLDNaeOj4MFSVEAH5uxR2Re', true, true, CURRENT_TIMESTAMP);

-- Link admin user to test band
INSERT INTO user_team_roles (user_id, team_id, role, assigned_at, invitation_accepted, invitation_accepted_at)
VALUES (1, 1, 'ADMIN', CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP);
