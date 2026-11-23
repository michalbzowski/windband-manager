package pl.michalbzowski.windband.domain.composition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Odczyt/zapis oznaczeń stron „nie wykorzystam" (raport spójności AI).
 *
 * <p>Dominant read path: wszystkie znaczenia dla jednego pliku nut (sekcja widoku
 * + obliczanie luk po stronie JS); usuwanie — pojedyncze {@code deleteById} po
 * znalezionym wierszu, bo dzielenie/skalanie zakresów jest logiką domenową,
 * nie query SQL.
 */
public interface ScorePageExclusionRepository extends JpaRepository<ScorePageExclusion, Long> {

    /** Oznaczenia przypięte do danego pliku (bez gwarancji porządku — sortuje warstwa komendowa). */
    List<ScorePageExclusion> findAllByScoreFile(ScoreFile scoreFile);
}
