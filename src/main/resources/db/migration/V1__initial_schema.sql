CREATE TABLE IF NOT EXISTS instruments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS members (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    osp_member BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE IF NOT EXISTS member_instruments (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(member_id, instrument_id)
);

CREATE TABLE IF NOT EXISTS rehearsals (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    location VARCHAR(255),
    notes TEXT
);

CREATE TABLE IF NOT EXISTS attendances (
    id BIGSERIAL PRIMARY KEY,
    rehearsal_id BIGINT NOT NULL REFERENCES rehearsals(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'NO_RESPONSE',
    UNIQUE(rehearsal_id, member_id)
);

CREATE TABLE IF NOT EXISTS band_events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    start_time TIME,
    location VARCHAR(255),
    event_type VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    notes TEXT
);

CREATE TABLE IF NOT EXISTS event_participations (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES band_events(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    response VARCHAR(20) NOT NULL DEFAULT 'NO_RESPONSE',
    payment_amount DECIMAL(10,2),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE',
    UNIQUE(event_id, member_id)
);

CREATE TABLE IF NOT EXISTS uniform_items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    member_id BIGINT REFERENCES members(id) ON DELETE SET NULL,
    ownership_status VARCHAR(20) NOT NULL DEFAULT 'MISSING'
);

CREATE TABLE IF NOT EXISTS instrument_items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    brand VARCHAR(100),
    serial_number VARCHAR(100),
    description VARCHAR(500),
    member_id BIGINT REFERENCES members(id) ON DELETE SET NULL,
    ownership_status VARCHAR(20) NOT NULL DEFAULT 'MISSING'
);

-- Envers audit tables (required for ddl-auto: validate; created manually to avoid Hibernate auto-creating them)
-- Note: With ddl-auto: update, Hibernate will create these tables automatically.
-- This section is kept as documentation of the expected schema.
-- CREATE TABLE revinfo (...);
-- CREATE TABLE members_aud (...);
-- etc.

-- Indexes
CREATE INDEX IF NOT EXISTS idx_members_active ON members(active);
CREATE INDEX IF NOT EXISTS idx_members_role ON members(role);
CREATE INDEX IF NOT EXISTS idx_rehearsals_date ON rehearsals(date);
CREATE INDEX IF NOT EXISTS idx_band_events_date ON band_events(date);
CREATE INDEX IF NOT EXISTS idx_attendances_rehearsal ON attendances(rehearsal_id);
CREATE INDEX IF NOT EXISTS idx_attendances_member ON attendances(member_id);
CREATE INDEX IF NOT EXISTS idx_event_participations_event ON event_participations(event_id);
CREATE INDEX IF NOT EXISTS idx_uniform_items_member ON uniform_items(member_id);
CREATE INDEX IF NOT EXISTS idx_instrument_items_member ON instrument_items(member_id);
