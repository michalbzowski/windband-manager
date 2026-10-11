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

-- ============================================================
-- Unified inventory (F2 S2).  Two bands, two item types.
-- Bands: 1 = Test Band, 2 = Other Band (isolation: band-2 rows
-- must never leak into band-1 views; names below are asserted
-- by TeamIsolationRegressionUiTest and InventoryUiTest).
-- Attribute defs idempotent H2 identity ids:
--   def 1 = Rozmiar  (UNIFORM, required INTEGER) — band 1
--   def 2 = Obój     (INSTRUMENT SELECT BB;A;ESB) — band 1
-- ============================================================

-- Attribute definitions for Test Band (band_id=1)
INSERT INTO item_attribute_defs (id, name, description, attribute_type, item_type, band_id, display_in_list, display_order, is_required, is_filterable, is_conditional, active)
VALUES (1, 'Rozmiar', 'Rozmiar czapki/uniformu', 'INTEGER', 'UNIFORM', 1, true, 10, true, true, false, true);
INSERT INTO item_attribute_defs (id, name, description, attribute_type, item_type, band_id, display_in_list, display_order, is_required, is_filterable, options, is_conditional, active)
VALUES (2, 'Obój', 'Typ obiegu instrumentu', 'SELECT', 'INSTRUMENT', 1, true, 10, false, true, 'BB;A;ESB', false, true);

-- Attribute definitions for Other Band (band_id=2) — must stay isolated from band-1 views
INSERT INTO item_attribute_defs (id, name, description, attribute_type, item_type, band_id, display_in_list, display_order, is_required, is_filterable, is_conditional, active)
VALUES (3, 'Rozmiar innego zespołu', 'Izolacja', 'INTEGER', 'UNIFORM', 2, true, 10, false, true, false, true);
-- The explicit ids above do NOT advance H2's identity sequence (PostgreSQL mode), so
-- restart it: auto-generated rows must not collide with seeded ids.
ALTER TABLE item_attribute_defs ALTER COLUMN id RESTART WITH 5;

-- Inventory items: Test Band (band_id=1)
-- Item id=1 = UNIFORM "Bluza Test"  ASSIGNED  to Jan Kowalski (member_id=1) — seed anchor for assign/return tests
INSERT INTO inventory_items (id, item_type, name, description, member_id, ownership_status, lifecycle_status, band_id, condition)
VALUES (1, 'UNIFORM', 'Bluza Test', 'Bluza orkiestrowa', 1, 'OWNED', 'ASSIGNED', 1, 'GOOD');
-- Item id=2 = INSTRUMENT "Trąbka Test" AVAILABLE in band-1 stock — UI assign/return target
INSERT INTO inventory_items (id, item_type, name, description, manufacturer, model, serial_number, ownership_status, lifecycle_status, band_id, condition)
VALUES (2, 'INSTRUMENT', 'Trąbka Test', 'Trąbka Bb', 'Yamaha', 'YTR-2335', 'SN001', 'OWNED', 'AVAILABLE', 1, 'GOOD');

-- Inventory items: Other Band (band_id=2) — must NEVER appear in Test Band views
INSERT INTO inventory_items (id, item_type, name, description, ownership_status, lifecycle_status, band_id, condition)
VALUES (3, 'UNIFORM', 'Bluza Other', 'Bluza innego zespołu', 'OWNED', 'AVAILABLE', 2, 'GOOD');
INSERT INTO inventory_items (id, item_type, name, description, manufacturer, model, serial_number, ownership_status, lifecycle_status, band_id, condition)
VALUES (4, 'INSTRUMENT', 'Saksofon Other', 'Saksofon alt', 'Selmer', NULL, 'SN002', 'OWNED', 'AVAILABLE', 2, 'GOOD');
-- Explicit seeded ids do NOT advance H2's identity sequence (PostgreSQL mode): restart it
-- so JPA auto-generated inserts (MemberDetail UiTest, PageHeader order seed, Inventory create)
-- never collide with the seeded ids above.
ALTER TABLE inventory_items ALTER COLUMN id RESTART WITH 99;

-- Attribute values for the seeded items: def ids 1 = Rozmiar, 2 = Obój.
-- Bluza Test (id=1) gets two integer attribute values; Trąbka Test (id=2)
-- carries a single text attribute value (Obój = BB).
INSERT INTO item_attribute_values (item_id, attribute_def_id, value_text, value_integer) VALUES (1, 1, '46', 46);
INSERT INTO item_attribute_values (item_id, attribute_def_id, value_text) VALUES (2, 2, 'BB');
ALTER TABLE item_attribute_values ALTER COLUMN id RESTART WITH 99;

-- Instruments (band=1 — the only band seeded in data.sql)
INSERT INTO instruments (name, description, sort_priority, band_id) VALUES ('Trąbka', 'Trąbka Bb', 1, 1);
INSERT INTO instruments (name, description, sort_priority, band_id) VALUES ('Bęben', 'Bęben wielki', 2, 1);
INSERT INTO instruments (name, description, sort_priority, band_id) VALUES ('Saksofon', 'Saksofon alt', 3, 1);

-- Member instruments (Jan plays Trąbka, Anna plays Bęben)
INSERT INTO member_instruments (member_id, instrument_id, is_primary)
VALUES (1, 1, true);
INSERT INTO member_instruments (member_id, instrument_id, is_primary)
VALUES (2, 2, true);

-- Events for Test Band (band_id=1) — dashboard shows the events list
INSERT INTO band_events (name, date, start_time, location, event_type, payment_type, payment_amount, notes, band_id)
VALUES ('Koncert Noworoczny', CURRENT_DATE + 30, '18:00', 'Rynek', 'CONCERT', 'FREE', NULL, 'Występ plenerowy', 1);
INSERT INTO band_events (name, date, start_time, location, event_type, payment_type, payment_amount, notes, band_id)
VALUES ('Parada 3 Maja', CURRENT_DATE - 10, '10:00', 'Park Miejski', 'PARADE', 'FREE', NULL, 'Uczestnictwo w paradzie', 1);

-- Rehearsals for Test Band (band_id=1) — for dashboard upcoming list with attendance
INSERT INTO rehearsals (date, start_time, end_time, location, notes, band_id)
VALUES (CURRENT_DATE + 1, '18:00', '20:00', 'Sala prób', 'Próba regularna', 1);
INSERT INTO rehearsals (date, start_time, end_time, location, notes, band_id)
VALUES (CURRENT_DATE + 2, '18:00', '21:00', 'Sala prób', 'Próba przed koncertem', 1);
INSERT INTO rehearsals (date, start_time, end_time, location, notes, band_id)
VALUES (CURRENT_DATE + 5, '19:00', '22:00', 'Sala prób', 'Próba weekendowa', 1);

-- Attendance for rehearsals (member 1 = Jan, member 2 = Anna) - band_id=1
-- Rehearsal 1 (id=1): both present
INSERT INTO attendances (rehearsal_id, member_id, status)
VALUES (1, 1, 'PRESENT');
INSERT INTO attendances (rehearsal_id, member_id, status)
VALUES (1, 2, 'PRESENT');
-- Rehearsal 2 (id=2): Jan present, Anna unexcused
INSERT INTO attendances (rehearsal_id, member_id, status)
VALUES (2, 1, 'PRESENT');
INSERT INTO attendances (rehearsal_id, member_id, status)
VALUES (2, 2, 'UNEXCUSED');
-- Rehearsal 3 (id=3): no attendance yet
