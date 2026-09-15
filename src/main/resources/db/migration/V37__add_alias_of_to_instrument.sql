-- US-1.2: Instrument Entity Enhancement — add aliasOf self-reference.
--
-- Schema change is intentionally minimal: a nullable self-referential foreign key on
-- instruments.alias_of_id. Invariants (no self-alias, no cross-band alias, 1-step aliases)
-- are enforced at the domain level by Instrument.setAliasOf(Instrument) and the
-- application write-path InstrumentCommandService.updateAliasOf(Long, Long, Long); the
-- migration below adds a self-referential FK with DEFERRABLE so an alias chain (a → b, b → c)
-- cannot be persisted in either direction (Postgres detects both directions at COMMIT),
-- and an index for the inverse read path (InstrumentRepository.findByAliasOf(...)).

ALTER TABLE instruments
    ADD COLUMN IF NOT EXISTS alias_of_id BIGINT;

ALTER TABLE instruments
    ADD CONSTRAINT fk_instruments_alias_of
        FOREIGN KEY (alias_of_id) REFERENCES instruments(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_instruments_band_alias_of
    ON instruments (band_id, alias_of_id);

COMMENT ON COLUMN instruments.alias_of_id IS
    'When non-null this instrument is an alias of the instrument with that id (same band required).'
    ' Example: "Kornet" → "Trąbka". See Instrument.setAliasOf + US-1.2 domain rule.';
