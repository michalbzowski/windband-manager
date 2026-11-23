-- V52: tabela oznaczeń stron „nie wykorzystam" w raporcie spójności AI.
--
-- Strony pliku nut nieprzydzielone żadnej partii mogą zostać trwale oznaczone
-- jako nieużywane (np. dodatkowe głosy utworu, z których kapelmistrz świadomie
-- nie korzysta). Oznaczenie to DANE AUDYTOWE: autor + chwila ostatniej modyfikacji;
-- usuwanie wiersza przy „Przywróć" jest twarde (zakres wraca do kolejki luk —
-- nie zostawiamy po nim śladu w tabeli, ostatni audyt znika razem z indeksem).
--
-- Semantyka relacji:
--   * composition_id CASCADE → usunięcie utworu usuwa jego oznaczenia;
--   * score_file_id NO ACTION (jak composition_instruments.instrument_id) —
--     oznaczenie przypięte do pliku blokujące usuwanie nagłówka pliku nut?
--     Nie: pliki nut zastępuje się w miejscu (zachowany id), nie kasuje wiersza;
--     Ograniczenie DB pilnuje wyłącznie osieroceń przez ścieżki serwisowe.
-- CHECK i unikat dokładnie jak w composition_instruments (spójny model zakresów).

CREATE TABLE IF NOT EXISTS score_page_exclusions (
    id          BIGSERIAL PRIMARY KEY,
    composition_id BIGINT NOT NULL,
    score_file_id  BIGINT NOT NULL,
    page_from     INTEGER NOT NULL CHECK (page_from >= 1),
    page_to       INTEGER NOT NULL CHECK (page_to   >= 1 AND page_to >= page_from),
    marked_by     VARCHAR(255) NOT NULL,
    marked_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_score_page_exclusions_composition
        FOREIGN KEY (composition_id) REFERENCES compositions (id) ON DELETE CASCADE,
    CONSTRAINT fk_score_page_exclusions_score_file
        FOREIGN KEY (score_file_id) REFERENCES score_files (id) ON DELETE RESTRICT
);

-- Jeden wiersz na dokładny zakres w tym samym pliku (dzielenie/skalanie zakresów
-- robi warstwa domenowa — repozytorium blokuje tylko pełne duplikaty).
DROP INDEX IF EXISTS uq_score_page_exclusions_file_range;
CREATE UNIQUE INDEX uq_score_page_exclusions_file_range
    ON score_page_exclusions (composition_id, score_file_id, page_from, page_to);

-- Dominant read path: „wszystkie oznaczenia pliku" (sekcja raportu + luki JS).
DROP INDEX IF EXISTS idx_score_page_exclusions_file_page;
CREATE INDEX idx_score_page_exclusions_file_page
    ON score_page_exclusions (score_file_id, page_from ASC);

COMMENT ON TABLE score_page_exclusions IS
    'Oznaczenia stron utworu „nie wykorzystam" (raport spójności AI): zakresy stron pliku nut, których użytkownik świadomie nie obsadza głosem';
COMMENT ON COLUMN score_page_exclusions.marked_by IS
    'Audyt: tożsamość osoby oznaczającej/przywracającej (id użytkownika); ostatni pisarz wygrywa — wiersz ma jednorazowy znacznik';
COMMENT ON COLUMN score_page_exclusions.score_file_id IS
    'Plik nut, do którego należą strony (przeród stron 1..page_count tego pliku)';
