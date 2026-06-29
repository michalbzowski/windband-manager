ALTER TABLE event_participations ADD COLUMN instrument_id BIGINT REFERENCES instruments(id);
