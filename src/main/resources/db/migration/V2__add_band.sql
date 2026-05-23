CREATE TABLE IF NOT EXISTS bands (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at DATE NOT NULL DEFAULT CURRENT_DATE
);

-- Insert default band first (before FK constraints reference it)
INSERT INTO bands (name, description, created_at)
VALUES ('MOD Strażak', 'Młodzieżowa Orkiestra Dęta Wojkowice Kościelne', CURRENT_DATE)
ON CONFLICT (name) DO NOTHING;

-- Add band_id to members
ALTER TABLE IF EXISTS members ADD COLUMN IF NOT EXISTS band_id BIGINT;
UPDATE members SET band_id = 1 WHERE band_id IS NULL;
ALTER TABLE members ALTER COLUMN band_id SET NOT NULL;
ALTER TABLE members ADD CONSTRAINT fk_members_band FOREIGN KEY (band_id) REFERENCES bands(id);
CREATE INDEX IF NOT EXISTS idx_members_band ON members(band_id);

-- Add band_id to rehearsals
ALTER TABLE IF EXISTS rehearsals ADD COLUMN IF NOT EXISTS band_id BIGINT;
UPDATE rehearsals SET band_id = 1 WHERE band_id IS NULL;
ALTER TABLE rehearsals ALTER COLUMN band_id SET NOT NULL;
ALTER TABLE rehearsals ADD CONSTRAINT fk_rehearsals_band FOREIGN KEY (band_id) REFERENCES bands(id);
CREATE INDEX IF NOT EXISTS idx_rehearsals_band ON rehearsals(band_id);

-- Add band_id to band_events
ALTER TABLE IF EXISTS band_events ADD COLUMN IF NOT EXISTS band_id BIGINT;
UPDATE band_events SET band_id = 1 WHERE band_id IS NULL;
ALTER TABLE band_events ALTER COLUMN band_id SET NOT NULL;
ALTER TABLE band_events ADD CONSTRAINT fk_band_events_band FOREIGN KEY (band_id) REFERENCES bands(id);
CREATE INDEX IF NOT EXISTS idx_band_events_band ON band_events(band_id);

-- Add band_id to uniform_items
ALTER TABLE IF EXISTS uniform_items ADD COLUMN IF NOT EXISTS band_id BIGINT;
UPDATE uniform_items SET band_id = 1 WHERE band_id IS NULL;
ALTER TABLE uniform_items ALTER COLUMN band_id SET NOT NULL;
ALTER TABLE uniform_items ADD CONSTRAINT fk_uniform_items_band FOREIGN KEY (band_id) REFERENCES bands(id);
CREATE INDEX IF NOT EXISTS idx_uniform_items_band ON uniform_items(band_id);

-- Add band_id to instrument_items
ALTER TABLE IF EXISTS instrument_items ADD COLUMN IF NOT EXISTS band_id BIGINT;
UPDATE instrument_items SET band_id = 1 WHERE band_id IS NULL;
ALTER TABLE instrument_items ALTER COLUMN band_id SET NOT NULL;
ALTER TABLE instrument_items ADD CONSTRAINT fk_instrument_items_band FOREIGN KEY (band_id) REFERENCES bands(id);
CREATE INDEX IF NOT EXISTS idx_instrument_items_band ON instrument_items(band_id);
