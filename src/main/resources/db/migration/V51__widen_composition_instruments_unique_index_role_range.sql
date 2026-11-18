-- Migration V51: the voice-block identity widens to (composition, role + page range).
--
-- WHY (prod 2026-11-18, regression "Amerland" followed by #343): arrangement.json
-- legitimately repeats one role with different page ranges ("Puzon I B♭" on p.3 AND
-- p.7 — two blocks of the same voice). #343 aligned the acceptance service (skip key =
-- role + range) and its tests, but the schema stayed at V38's narrower rule:
--     UNIQUE (composition_id, lower(instrument_role))
-- so accepting BOTH legal blocks died on
--     ERROR: duplicate key value violates unique constraint "uq_composition_instruments_role"
-- with the raw SQL surfacing to the user (500).
--
-- The index now mirrors the application's identity exactly: one part per
-- (composition, casefolded role, page range); the same role on other pages is a
-- distinct voice block — two legal rows.
--
-- Steps (idempotent-safe within Postgres transaction):
--   1. dedupe exact-key collisions that the NEW index would refuse to hold: for each
--      (composition_id, lower(instrument_role), page_from, page_to) group keep the
--      newest row (MAX(id)) and drop the rest. Identical-key rows are interchangeable
--      data (same role, same pages); nothing unique is lost by keeping one.
--   2. swap the index to the widened definition (V38's drop-then-create pattern).

DELETE FROM composition_instruments
WHERE id NOT IN (
    SELECT MAX(id)
    FROM composition_instruments
    GROUP BY composition_id, lower(instrument_role), page_from, page_to
);

DROP INDEX IF EXISTS uq_composition_instruments_role;
CREATE UNIQUE INDEX uq_composition_instruments_role
    ON composition_instruments (composition_id, lower(instrument_role), page_from, page_to);

COMMENT ON INDEX uq_composition_instruments_role IS
    'One part per (composition, casefolded role, page range) — V51: same role on different pages is a distinct voice block (#343 / prod „Amerland" 2026-11-18).';
