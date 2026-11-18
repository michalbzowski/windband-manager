package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Kontrakt odpowiedzi serwisów web na błędy integralności (równoległa akceptacja
 * tej samej partii głosu): klient dostaje czytelne 409 po polsku, a NIGDY surowe
 * SQL z ciała wyjątku (regresja prod 2026-11-18: pełny komunikat "duplicate key ..."
 * trafiał do widżeta błędu w detal utworu).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DataIntegrityViolationException → 409 z ludzką wiadomością PL, bez surowego SQL")
    void dataIntegrityConflictSurfacesAsPolish409WithoutRawSql() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uq_composition_instruments_role\" "
                        + "Detail: Key (composition_id, lower(instrument_role::text))=(12, sakshorn barytonowy b♭) already exists.");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String error = String.valueOf(response.getBody().get("error"));
        assertThat(error).containsIgnoringCase("koliduje");
        assertThat(error).doesNotContain("duplicate key");
        assertThat(error).doesNotContain("uq_composition_instruments_role");
    }
}
