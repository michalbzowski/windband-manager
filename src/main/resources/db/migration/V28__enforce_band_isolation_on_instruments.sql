-- V28: Enforce band isolation on instruments (tags)
-- Bug: SpringDataInstrumentRepository.findAllByBandIdOrderBySortPriorityAsc
--      used "i.band.id = :bandId OR i.band IS NULL", which leaked legacy
--      band_id=NULL rows to every team. New teams saw the 13 V13-seeded
--      instruments (Trąbka, Flet, Oboj, Klarnet, Fagot, Saksofon x3, Róg,
--      Puzon, Tuba, Perkusja, Dyrygent) even without creating them.
-- Fix: assign band_id=1 to every NULL row (these are the original seed
--      catalog that was global before multi-tenant support; assigning them
--      to band 1 is the natural default and matches the historical UX).
--      Then enforce NOT NULL and update the repository query to drop the
--      "OR i.band IS NULL" clause so cross-team leakage is impossible.

UPDATE instruments SET band_id = 1 WHERE band_id IS NULL;

-- Enforce band ownership going forward
ALTER TABLE instruments ALTER COLUMN band_id SET NOT NULL;