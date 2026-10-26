package pl.michalbzowski.windband.adapter.out.scoreanalyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.michalbzowski.windband.application.command.scoreanalysis.AiAnalysisRunner.AnalysisRequest;
import pl.michalbzowski.windband.application.command.scoreanalysis.AiAnalysisRunner.PhaseStatus;
import pl.michalbzowski.windband.application.command.scoreanalysis.AiAnalysisRunner.RunnerNotConfiguredException;
import pl.michalbzowski.windband.application.command.scoreanalysis.JsonAiProposals;
import pl.michalbzowski.windband.application.config.ScoreAnalysisConfig;
import pl.michalbzowski.windband.application.dto.scoreanalysis.AIProposals;

/**
 * Phase B — unit tests of the Score Analyzer HTTP adapter against a JDK {@link HttpServer}
 * mock (no new test deps). Covers: multipart start + credential headers, phase mapping,
 * artefact materialisation on success (arrangement.json round-trip through the production
 * parser {@link JsonAiProposals}), failure paths (analyzer error, 404/TTL, unreachable host).
 */
class HttpScoreAnalyzerRunnerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

    /** Realistic single-file analyzer result (shape pinned by the score-analyzer repo). */
    private static final String RESULT_JSON = """
            {"file_name": "polonez.pdf", "total_pages": 15, "detection_mode": "text-layer",
             "work": {"title": "Polonez Z Filmu PAN TADEUSZ", "composer": "Krzysztof Flak",
                      "arranger": "Ireneusz Stromski"},
             "parts": [{"instrument": "Partytura", "kind": "partytura",
                        "ranges": [{"from": 1, "to": 15}], "pages": "1-15"}],
             "warnings": []}""";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> jobStatus = new AtomicReference<>("pending");
    private final Map<String, String> capturedHeaders = new ConcurrentHashMap<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>("");

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jobs", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod())) {
                String analyzerKey = exchange.getRequestHeaders().getFirst("X-Analyzer-Key");
                if (analyzerKey != null) capturedHeaders.put("analyzer", analyzerKey);
                String llmKey = exchange.getRequestHeaders().getFirst("X-LLM-Key");
                if (llmKey != null) capturedHeaders.put("llm", llmKey);
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
                respond(exchange, 202, "{\"jobs\":[{\"job_id\":\"" + JOB_ID
                        + "\",\"file_name\":\"polonez.pdf\"}]}");
            } else if (path.endsWith("/result")) {
                if (!"succeeded".equals(jobStatus.get())) {
                    respond(exchange, 409, "{\"detail\":\"Job w fazie " + jobStatus.get() + "\"}");
                } else {
                    respond(exchange, 200, RESULT_JSON);
                }
            } else {
                respond(exchange, 200, "{\"job_id\":\"" + JOB_ID + "\",\"file_name\":\"polonez.pdf\","
                        + "\"status\":\"" + jobStatus.get() + "\",\"error\":"
                        + ("failed".equals(jobStatus.get()) ? "\"boom z analyzera\"" : "null") + "}");
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        jobStatus.set("pending");
        capturedHeaders.clear();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String body)
            throws IOException {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, raw.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(raw);
        }
    }

    private HttpScoreAnalyzerRunner runner() {
        return new HttpScoreAnalyzerRunner(
                new ScoreAnalysisConfig(null, baseUrl, "shared-secret", 30));
    }

    private AnalysisRequest request(Path outDir) throws IOException {
        Path pdf = tmp.resolve("polonez.pdf");
        Files.writeString(pdf, "%PDF-1.4 test content");
        return new AnalysisRequest(pdf, outDir, "test-llm-key");
    }

    private AnalysisRequest taggedRequest(List<String> tags) throws IOException {
        Path pdf = tmp.resolve("polonez.pdf");
        Files.writeString(pdf, "%PDF-1.4 test content");
        return new AnalysisRequest(pdf, tmp.resolve("out"), "test-llm-key", tags);
    }

    @Test
    @DisplayName("start: multipart upload zwraca job_id i wysyła oba nagłówki uwierzytelniające")
    void startUploadsPdfWithCredentials() throws Exception {
        String ref = runner().start(request(tmp.resolve("out")));

        assertThat(ref).isEqualTo(JOB_ID);
        assertThat(capturedHeaders).containsEntry("analyzer", "shared-secret")
                .containsEntry("llm", "test-llm-key");
        String body = capturedBody.get();
        assertThat(body).contains("name=\"files\"").contains("filename=\"polonez.pdf\"")
                .contains("%PDF-1.4 test content");
    }

    @Test
    @DisplayName("start z listą tagów zespołu: pole instruments (JSON) trafia do multipartu obok PDF-a")
    void startWithTeamInstruments_sendsInstrumentsFormField() throws Exception {
        runner().start(taggedRequest(List.of("Sax Tenor", "Eufonium")));

        String body = capturedBody.get();
        assertThat(body).contains("name=\"files\"");
        assertThat(body).contains("Content-Disposition: form-data; name=\"instruments\"")
                .contains("[\"Sax Tenor\",\"Eufonium\"]");
        // struktura multipartu: początek od separatora, koniec zamkniętym separatorem
        assertThat(body).startsWith("--wm-").endsWith("\r\n");
    }

    @Test
    @DisplayName("start bez tagów: pole instruments nie istnieje w ciele żądania")
    void startWithoutTeamInstruments_omitsInstrumentsField() throws Exception {
        runner().start(request(tmp.resolve("out")));

        assertThat(capturedBody.get()).doesNotContain("instruments");
    }

    @Test
    @DisplayName("Pełny obieg: pending → running → succeeded zapisuje arrangement.json czytelny dla JsonAiProposals")
    void fullLifecycleWritesArtefacts() throws Exception {
        HttpScoreAnalyzerRunner runner = runner();
        Path outDir = tmp.resolve("out");
        String ref = runner.start(request(outDir));

        assertThat(runner.inspect(ref).phase()).isEqualTo(PhaseStatus.Phase.PENDING);
        jobStatus.set("running");
        assertThat(runner.inspect(ref).phase()).isEqualTo(PhaseStatus.Phase.RUNNING);

        jobStatus.set("succeeded");
        PhaseStatus done = runner.inspect(ref);
        assertThat(done.phase()).isEqualTo(PhaseStatus.Phase.SUCCEEDED);
        assertThat(done.artefactRootOrNull()).isEqualTo(outDir);

        // arrangement.json — kontrakt czytany produkcyjnie przez JsonAiProposals
        String arrangement = Files.readString(outDir.resolve("arrangement.json"));
        List<AIProposals> rows = JsonAiProposals.parse(arrangement);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).role()).isEqualTo("Partytura");
        assertThat(rows.get(0).pageFrom()).isEqualTo(1);
        assertThat(rows.get(0).pageTo()).isEqualTo(15);
        assertThat(rows.get(0).confidence()).isEqualTo(HttpScoreAnalyzerRunner.CONFIDENCE_TEXT_LAYER);

        // surowy wynik + podsumowanie walidacji
        assertThat(MAPPER.readTree(Files.readString(outDir.resolve("analyzer-raw.json")))
                .path("work").path("composer").asText()).isEqualTo("Krzysztof Flak");
        String validation = Files.readString(outDir.resolve("validation.txt"));
        assertThat(validation).contains("Polonez Z Filmu PAN TADEUSZ")
                .contains("text-layer").contains("Ireneusz Stromski");
    }

    @Test
    @DisplayName("inspect: status failed z analitycznego serwisu mapuje się na FAILED z jego komunikatem")
    void failedStatusMapsToFailed() throws Exception {
        HttpScoreAnalyzerRunner runner = runner();
        String ref = runner.start(request(tmp.resolve("out")));
        jobStatus.set("failed");

        PhaseStatus st = runner.inspect(ref);
        assertThat(st.phase()).isEqualTo(PhaseStatus.Phase.FAILED);
        assertThat(st.errorMessageIfFailed()).isEqualTo("boom z analyzera");
    }

    @Test
    @DisplayName("inspect: 404 (job wygasł / restart serwisu) → FAILED z polskim komunikatem o ponowieniu")
    void unknownJobMapsToFailed() throws Exception {
        HttpServer s404 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s404.createContext("/jobs", ex -> respond(ex, 404, "{\"detail\":\"Job nieznany\"}"));
        s404.start();
        PhaseStatus st;
        try {
            HttpScoreAnalyzerRunner r404 = new HttpScoreAnalyzerRunner(new ScoreAnalysisConfig(
                    null, "http://127.0.0.1:" + s404.getAddress().getPort(), null, 10));
            st = r404.inspect(JOB_ID);
        } finally {
            s404.stop(0);
        }
        assertThat(st.phase()).isEqualTo(PhaseStatus.Phase.FAILED);
        assertThat(st.errorMessageIfFailed()).contains("uruchom analizę ponownie");
    }

    @Test
    @DisplayName("start: nieosiągalny serwis → RunnerNotConfiguredException (wiersz FAILED, HTTP 501)")
    void unreachableServiceThrowsNotConfigured() throws Exception {
        HttpScoreAnalyzerRunner dead = new HttpScoreAnalyzerRunner(
                new ScoreAnalysisConfig(null, "http://127.0.0.1:1", null, 2));
        assertThatThrownBy(() -> dead.start(request(tmp.resolve("out"))))
                .isInstanceOf(RunnerNotConfiguredException.class)
                .hasMessageContaining("niedostępny");
    }

    @Test
    @DisplayName("start: HTTP 401 (zły klucz serwisowy) → RunnerNotConfiguredException")
    void unauthorizedStartThrows() throws Exception {
        HttpServer s401 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s401.createContext("/jobs", ex -> respond(ex, 401, "{\"detail\":\"brak klucza\"}"));
        s401.start();
        try {
            HttpScoreAnalyzerRunner r = new HttpScoreAnalyzerRunner(new ScoreAnalysisConfig(
                    null, "http://127.0.0.1:" + s401.getAddress().getPort(), "bad", 10));
            assertThatThrownBy(() -> r.start(request(tmp.resolve("out"))))
                    .isInstanceOf(RunnerNotConfiguredException.class)
                    .hasMessageContaining("401");
        } finally {
            s401.stop(0);
        }
    }

    @Test
    @DisplayName("Mapper: wiele zakresów jednego głosu → wiele wierszy; scanned → niższy confidence")
    void mapperHandlesRangesAndModes() throws Exception {
        HttpScoreAnalyzerRunner runner = runner();
        String multi = """
                {"file_name":"g.pdf","total_pages":65,"detection_mode":"scanned",
                 "parts":[
                   {"instrument":"Klarnet I B♭","kind":"glos",
                    "ranges":[{"from":1,"to":2},{"from":10,"to":12}],"pages":"1-2, 10-12"},
                   {"instrument":"","kind":"nieznany","ranges":[{"from":5,"to":5}],"pages":"5"}],
                 "warnings":["gaps"]}""";
        List<AIProposals> rows = JsonAiProposals.parse(
                runner.toArrangementJson(MAPPER.readTree(multi)));

        assertThat(rows).hasSize(2);       // pusty instrument odrzucony
        assertThat(rows).extracting(AIProposals::role)
                .containsExactly("Klarnet I B♭", "Klarnet I B♭");
        assertThat(rows.get(0).pageFrom()).isEqualTo(1);
        assertThat(rows.get(1).pageFrom()).isEqualTo(10);
        assertThat(rows.get(1).pageTo()).isEqualTo(12);
        assertThat(rows.get(0).confidence())
                .isEqualTo(HttpScoreAnalyzerRunner.CONFIDENCE_SCANNED);
    }

    @Test
    @DisplayName("Konfiguracja: pusty analyzer-base-url jest normalizowany do braku analyzera (Stub)")
    void blankBaseUrlMeansNoAnalyzer() {
        assertThat(new ScoreAnalysisConfig(null, "", "", null).hasAnalyzer()).isFalse();
        assertThat(new ScoreAnalysisConfig(null, " http://x ", null, null).analyzerBaseUrl())
                .isEqualTo("http://x");
        assertThatThrownBy(() -> new HttpScoreAnalyzerRunner(new ScoreAnalysisConfig(null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
