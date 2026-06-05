-- V13__add_instrument_sort_priority.sql
-- Add sort_priority column to instruments for issue #25
-- Enables ordering instruments by priority (lower number = higher priority)

ALTER TABLE instruments ADD COLUMN sort_priority INTEGER DEFAULT 0;

-- Set default priorities for common instruments
UPDATE instruments SET sort_priority = 0 WHERE name = 'Dyrygent';
UPDATE instruments SET sort_priority = 1 WHERE name = 'Flet';
UPDATE instruments SET sort_priority = 2 WHERE name = 'Oboj';
UPDATE instruments SET sort_priority = 3 WHERE name = 'Klarnet';
UPDATE instruments SET sort_priority = 4 WHERE name = 'Fagot';
UPDATE instruments SET sort_priority = 5 WHERE name = 'Saksofon Alt';
UPDATE instruments SET sort_priority = 6 WHERE name = 'Saksofon Tenor';
UPDATE instruments SET sort_priority = 7 WHERE name = 'Saksofon Bariton';
UPDATE instruments SET sort_priority = 8 WHERE name = 'Trąbka';
UPDATE instruments SET sort_priority = 9 WHERE name = 'Róg';
UPDATE instruments SET sort_priority = 10 WHERE name = 'Puzon';
UPDATE instruments SET sort_priority = 11 WHERE name = 'Tuba';
UPDATE instruments SET sort_priority = 12 WHERE name = 'Perkusja';