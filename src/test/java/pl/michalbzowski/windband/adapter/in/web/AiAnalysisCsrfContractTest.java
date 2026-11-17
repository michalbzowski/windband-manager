package pl.michalbzowski.windband.adapter.in.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kontrakt CSRF dla surowych {@code fetch()} w dialogu analizy AI
 * ({@code templates/compositions/detail.html}).
 *
 * <p><b>Dlaczego osobny test, skoro mamy suitę Selenium.</b> W profilu testowym
 * {@code UiTestBase} instaluje interceptor, który <em>dokleja</em>
 * {@code X-XSRF-TOKEN} do każdej mutującej {@code fetch}/{@code XHR} — więc
 * UI-testy nigdy nie zobaczą brakującego headera, nawet gdy aplikacja go nie
 * wysyła. W produkcji header musi pochodzić od samego JS. Bez niego Spring
 * Security (CSRF włączony globalnie, ścieżki nie-/api) odpowiada 302 → '/',
 * fetch podąża za przekierowaniem i dostaje HTML z {@code resp.ok=true}.
 * Objaw produkcyjny (2026-11-17): „Akceptuj zaznaczone” pokazywało
 * „Utworzono ? partie — Odświeżam widok utworu…”, a do bazy nie trafiła żadna
 * partia; analogicznie POST /analyze „udawał” start analizy, bo pollNext()
 * zgłaszał SUCCEEDED poprzedniego runu.
 *
 * <p>To jest test statyczny klasy zasobów: pilnuje, żeby KAŻDY mutujący fetch
 * w IIFE analizy wysyłał token przez {@code csrfHeadersAi()} i żeby odpowiedź
 * akceptacji była weryfikowana po content-type (anty-maskarada sukcesu).
 */
class AiAnalysisCsrfContractTest {

    private static final String TEMPLATE = "/templates/compositions/detail.html";

    private static String template() {
        try (InputStream in = AiAnalysisCsrfContractTest.class
                .getResourceAsStream(TEMPLATE)) {
            assertThat(in).as("szablon utworu musi być w classpath (resource packaging!)").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Okno tekstu zaczynające się na pierwszym wystąpieniu markera. */
    private static String window(String source, String marker, int length) {
        int idx = source.indexOf(marker);
        assertThat(idx).as("marker %s nie znaleziony w %s", marker, TEMPLATE).isNotNegative();
        return source.substring(idx, Math.min(idx + length, source.length()));
    }

    @Test
    @DisplayName("csrfHeadersAi() realnie wysyła X-XSRF-TOKEN z pliku cookie")
    void csrfHelperSendsXsrfHeader() {
        String block = window(template(), "function csrfHeadersAi()", 600);
        assertThat(block)
                .as("helper musi czytać cookie XSRF-TOKEN i ustawiać nagłówek")
                .contains("getCookie('XSRF-TOKEN')")
                .contains("X-XSRF-TOKEN");
    }

    @Test
    @DisplayName("POST /accept wysyła token CSRF i uznaje sukces tylko przy JSON (anty-302-maskarada)")
    void acceptFetchSendsCsrfAndGuardsContentType() {
        String src = template();
        String block = window(src, "/analysis/' + aiAnalysisId + '/accept'", 1400);
        assertThat(block)
                .as("accept musi używać csrfHeadersAi() + credentials")
                .contains("csrfHeadersAi()")
                .contains("same-origin");
        assertThat(block)
                .as("sukces tylko gdy Content-Type application/json — przekierowany HTML nie może udawać 200-OK")
                .contains("application/json");
    }

    @Test
    @DisplayName("POST /analyze wysyła token CSRF (fetch, nie htmx — hook layout.html tu nie działa)")
    void analyzeFetchSendsCsrf() {
        String block = window(template(), "/score-files/' + state.fileId + '/analyze'", 500);
        assertThat(block)
                .as("start analizy musi używać csrfHeadersAi()")
                .contains("csrfHeadersAi()");
    }

    @Test
    @DisplayName("POST /api/instruments z „+ utwórz tag” wysyła token CSRF (regresja ReferenceError)")
    void createTagFetchSendsCsrf() {
        String block = window(template(), "'/api/instruments'", 400);
        assertThat(block)
                .as("tworzenie tagu musi używać csrfHeadersAi()")
                .contains("csrfHeadersAi()")
                .doesNotContain("getCookieLocal");
    }
}
