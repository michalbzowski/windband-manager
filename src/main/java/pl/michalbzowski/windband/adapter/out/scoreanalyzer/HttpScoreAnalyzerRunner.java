package pl.michalbzowski.windband.adapter.out.scoreanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.michalbzowski.windband.application.command.scoreanalysis.AiAnalysisRunner;
import pl.michalbzowski.windband.application.config.ScoreAnalysisConfig;

/**
 * Score Analyzer integration (Phase B) — {@link AiAnalysisRunner} adapter talking to the
 * external Python service (repo {@code score-analyzer}) over its async jobs API:
 *
 * <ul>
 *   <li>{@code start}  = multipart upload of the ScoreFile PDF to {@code POST /jobs} →
 *       the analyzer's {@code job_id} becomes the {@code runnerRef}.</li>
 *   <li>{@code inspect} = {@code GET /jobs/{ref}}; on {@code succeeded} it fetches
 *       {@code GET /jobs/{ref}/result}, maps {@code parts[].ranges[]} to the
 *       {@code arrangement.json} contract ({@code role,pageFrom,pageTo,confidence} — read by
 *       {@link pl.michalbzowski.windband.application.command.scoreanalysis.JsonAiProposals}),
 *       writes {@code analyzer-raw.json} + {@code validation.txt} and returns SUCCEEDED.</li>
 * </ul>
 *
 * <p>Credentials: {@code X-Analyzer-Key} (shared service token, from config) and
 * {@code X-LLM-Key} (per-run user LLM key, from {@link AnalysisRequest#llmApiKey()} —
 * in memory only, never logged).
 *
 * <p>Failure policy: transport errors while STARTING map to
 * {@link RunnerNotConfiguredException} (service unavailable → command service fails the row).
 * Transport errors while INSPECTING map to FAILED with a Polish message — analyzer jobs are
 * in-memory with a 1 h TTL, so a WM or analyzer restart loses them and the honest outcome is
 * "rerun the analysis".
 *
 * <p>Uses the JDK {@link HttpClient} + Jackson (already on the classpath); no Spring Web
 * types, so the application layer stays clean per ArchUnit.
 */
public final class HttpScoreAnalyzerRunner implements AiAnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(HttpScoreAnalyzerRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Confidence assigned per detection mode — text layer is deterministic, OCR is not. */
    static final double CONFIDENCE_TEXT_LAYER = 0.95d;
    static final double CONFIDENCE_MIXED = 0.85d;
    static final double CONFIDENCE_SCANNED = 0.75d;

    private final String baseUrl;
    private final String sharedToken;          // nullable
    private final Duration requestTimeout;
    private final HttpClient http;
    /** runnerRef (analyzer job_id) → outputDir; needed because inspect() only gets the ref. */
    private final Map<String, Path> outputDirs = new ConcurrentHashMap<>();

    public HttpScoreAnalyzerRunner(ScoreAnalysisConfig cfg) {
        // HTTP/1.1 wymuszone: domyślny upgrade h2c JDK HttpClienta (Upgrade: h2c +
        // HTTP2-Settings) sprawia, że uvicorn/h11 NIE przekazuje body do FastAPI
        // i /jobs odpowiada 422 "files: Field required" — zweryfikowane empirycznie.
        this(cfg, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    HttpScoreAnalyzerRunner(ScoreAnalysisConfig cfg, HttpClient http) {
        if (cfg == null || !cfg.hasAnalyzer()) {
            throw new IllegalStateException(
                    "HttpScoreAnalyzerRunner wymaga app.scoreanalysis.analyzer-base-url");
        }
        String base = cfg.analyzerBaseUrl().trim();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.sharedToken = cfg.analyzerSharedToken();
        this.requestTimeout = Duration.ofSeconds(cfg.analyzerTimeoutSecondsOr());
        this.http = http;
    }

    @Override
    public String start(AnalysisRequest request) {
        byte[] pdf;
        try {
            pdf = Files.readAllBytes(request.inputPath());
        } catch (IOException e) {
            throw new RunnerNotConfiguredException(
                    "Nie udało się odczytać pliku do analizy: " + e.getMessage());
        }
        String boundary = "wm-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, String.valueOf(request.inputPath().getFileName()), pdf);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/jobs"))
                .timeout(requestTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (sharedToken != null) {
            b.header("X-Analyzer-Key", sharedToken);
        }
        if (request.llmApiKey() != null && !request.llmApiKey().isBlank()) {
            b.header("X-LLM-Key", request.llmApiKey());
        }

        HttpResponse<String> resp;
        try {
            log.info("Wysyłam plik do Score Analyzer: {} -> POST {}/jobs",
                    request.inputPath().getFileName(), baseUrl);
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RunnerNotConfiguredException(
                    "Serwis Score Analyzer jest niedostępny (" + baseUrl + "): ["
                            + e.getClass().getSimpleName() + "] " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RunnerNotConfiguredException("Przerwano wysyłkę pliku do Score Analyzer.");
        }
        if (resp.statusCode() != 200 && resp.statusCode() != 202) {
            String detail = resp.body() == null || resp.body().isBlank()
                    ? "" : ": " + resp.body().strip();
            if (detail.length() > 300) {
                detail = detail.substring(0, 300) + "…";
            }
            throw new RunnerNotConfiguredException(
                    "Serwis Score Analyzer odrzucił zadanie (HTTP " + resp.statusCode() + ")" + detail);
        }
        String jobId;
        try {
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode jobs = root.path("jobs");
            if (!jobs.isArray() || jobs.isEmpty() || jobs.get(0).path("job_id").asText("").isBlank()) {
                throw new IOException("brak jobs[0].job_id w odpowiedzi");
            }
            jobId = jobs.get(0).path("job_id").asText();
        } catch (IOException e) {
            throw new RunnerNotConfiguredException(
                    "Niepoprawna odpowiedź Score Analyzer przy starcie zadania: " + e.getMessage());
        }
        outputDirs.put(jobId, request.outputDir());
        log.info("Score Analyzer job {} wystartowany dla {}", jobId, request.inputPath().getFileName());
        return jobId;
    }

    @Override
    public PhaseStatus inspect(String runnerRef) {
        if (runnerRef == null || runnerRef.isBlank()) {
            return PhaseStatus.failed("Brak identyfikatora zadania analizy.");
        }
        HttpResponse<String> status;
        try {
            status = get("/jobs/" + enc(runnerRef));
        } catch (IOException e) {
            return PhaseStatus.failed("Serwis Score Analyzer jest niedostępny: " + e.getMessage());
        }
        if (status.statusCode() == 404) {
            return PhaseStatus.failed(
                    "Zadanie analizy wygasło lub serwis Score Analyzer został zrestartowany"
                            + " — uruchom analizę ponownie.");
        }
        if (status.statusCode() != 200) {
            return PhaseStatus.failed(
                    "Serwis Score Analyzer zwrócił błąd HTTP " + status.statusCode() + ".");
        }
        String phase;
        String error;
        try {
            JsonNode root = MAPPER.readTree(status.body());
            phase = root.path("status").asText("");
            error = root.path("error").asText(null);
        } catch (IOException e) {
            return PhaseStatus.failed("Niepoprawna odpowiedź statusu Score Analyzer: " + e.getMessage());
        }
        switch (phase) {
            case "pending":
                return new PhaseStatus(PhaseStatus.Phase.PENDING, null, null);
            case "running":
                return PhaseStatus.running();
            case "failed":
                return PhaseStatus.failed(error == null || error.isBlank()
                        ? "Analiza AI w serwisie Score Analyzer nie powiodła się." : error);
            case "succeeded":
                return collectResult(runnerRef);
            default:
                return PhaseStatus.failed("Nieznany status zadania Score Analyzer: " + phase);
        }
    }

    /** Fetches the result of a succeeded job and materialises the artefacts on disk. */
    private PhaseStatus collectResult(String runnerRef) {
        Path outDir = outputDirs.get(runnerRef);
        if (outDir == null) {
            // WM restart between start and succeeded — the layout is deterministic per
            // analysis id but this adapter only knows the ref; honest failure beats guessing.
            return PhaseStatus.failed(
                    "Aplikacja została zrestartowana w trakcie analizy — uruchom analizę ponownie.");
        }
        HttpResponse<String> result;
        try {
            result = get("/jobs/" + enc(runnerRef) + "/result");
        } catch (IOException e) {
            return PhaseStatus.failed("Nie udało się pobrać wyniku ze Score Analyzer: " + e.getMessage());
        }
        if (result.statusCode() != 200) {
            return PhaseStatus.failed(
                    "Serwis Score Analyzer zwrócił błąd HTTP " + result.statusCode()
                            + " przy pobieraniu wyniku.");
        }
        JsonNode analysis;
        try {
            analysis = MAPPER.readTree(result.body());
        } catch (IOException e) {
            return PhaseStatus.failed("Niepoprawny JSON wyniku Score Analyzer: " + e.getMessage());
        }
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve("arrangement.json"),
                    toArrangementJson(analysis), StandardCharsets.UTF_8);
            Files.writeString(outDir.resolve("analyzer-raw.json"),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(analysis),
                    StandardCharsets.UTF_8);
            Files.writeString(outDir.resolve("validation.txt"),
                    toValidationText(analysis), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return PhaseStatus.failed("Nie udało się zapisać artefaktów analizy: " + e.getMessage());
        }
        outputDirs.remove(runnerRef);
        return PhaseStatus.succeeded(outDir);
    }

    private HttpResponse<String> get(String path) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (sharedToken != null) {
            b.header("X-Analyzer-Key", sharedToken);
        }
        try {
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("przerwane żądanie do Score Analyzer", e);
        }
    }

    /**
     * Maps one analyzer file-result to the {@code arrangement.json} contract: a JSON array of
     * {@code {role, pageFrom, pageTo, confidence}} rows — one row per page range, so a part
     * split across the file keeps its exact ranges. Confidence reflects the detection mode
     * (text layer vs OCR), see {@code CONFIDENCE_*} constants.
     */
    String toArrangementJson(JsonNode analysis) throws IOException {
        ArrayNode rows = MAPPER.createArrayNode();
        double confidence = confidenceFor(analysis.path("detection_mode").asText(""));
        for (JsonNode part : analysis.path("parts")) {
            String role = part.path("instrument").asText("");
            if (role.isBlank()) {
                continue;                                   // a row without role is unusable downstream
            }
            for (JsonNode range : part.path("ranges")) {
                int from = Math.max(1, range.path("from").asInt(1));
                int to = Math.max(from, range.path("to").asInt(from));
                ObjectNode row = MAPPER.createObjectNode();
                row.put("role", role);
                row.put("pageFrom", from);
                row.put("pageTo", to);
                row.put("confidence", confidence);
                rows.add(row);
            }
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
    }

    static double confidenceFor(String detectionMode) {
        return switch (detectionMode == null ? "" : detectionMode) {
            case "text-layer" -> CONFIDENCE_TEXT_LAYER;
            case "mixed" -> CONFIDENCE_MIXED;
            default -> CONFIDENCE_SCANNED;
        };
    }

    /** Human-readable summary kept next to arrangement.json (path recorded on the row). */
    private String toValidationText(JsonNode analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("Plik: ").append(analysis.path("file_name").asText("?")).append('\n');
        sb.append("Stron: ").append(analysis.path("total_pages").asInt(0)).append('\n');
        sb.append("Tryb detekcji: ").append(analysis.path("detection_mode").asText("?")).append('\n');
        JsonNode work = analysis.path("work");
        if (work.isObject() && !work.isNull()) {
            sb.append("Tytuł: ").append(work.path("title").asText("—")).append('\n');
            sb.append("Kompozytor: ").append(work.path("composer").asText("—")).append('\n');
            sb.append("Aranżer: ").append(work.path("arranger").asText("—")).append('\n');
        }
        sb.append("Części: ").append(analysis.path("parts").size()).append('\n');
        JsonNode warnings = analysis.path("warnings");
        if (warnings.isArray() && !warnings.isEmpty()) {
            sb.append("Ostrzeżenia:\n");
            for (JsonNode w : warnings) {
                sb.append(" - ").append(w.asText()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Minimal single-file multipart/form-data body (RFC 7578) — one PDF part. */
    static byte[] multipartBody(String boundary, String fileName, byte[] fileBytes) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\""
                + fileName.replace("\"", "") + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] headB = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailB = tail.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headB.length + fileBytes.length + tailB.length];
        System.arraycopy(headB, 0, out, 0, headB.length);
        System.arraycopy(fileBytes, 0, out, headB.length, fileBytes.length);
        System.arraycopy(tailB, 0, out, headB.length + fileBytes.length, tailB.length);
        return out;
    }
}
