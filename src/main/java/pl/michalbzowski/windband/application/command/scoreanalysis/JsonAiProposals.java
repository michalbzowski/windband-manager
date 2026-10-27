package pl.michalbzowski.windband.application.command.scoreanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pl.michalbzowski.windband.application.dto.scoreanalysis.AIProposals;
import pl.michalbzowski.windband.domain.composition.PartSource;

/**
 * Reader for the {@code arrangement.json} artefact emitted by an AI analysis runner:
 * a JSON array of role proposals, each carrying a page range and a confidence.
 *
 * <pre>
 * [ {"role": "Flet C", "pageFrom": 1, "pageTo": 2, "confidence": 0.95}, ... ]
 * </pre>
 *
 * <ul>
 *   <li>Key order-insensitive, tolerant of loose key names a VLM might emit
 *       ({@code page_from}, {@code from}, {@code PAGEFROM}…).</li>
 *   <li>Defaults (page 1, confidence 1.0) keep a slightly malformed artefact useful, but a row
 *       without a role still throws rather than maps to the wrong part.</li>
 *   <li>Page order and confidence bounds are clamped so the {@link AIProposals} record
 *       invariants never fail on AI sloppiness.</li>
 * </ul>
 *
 * <p>History: the original hand-rolled tokenizer could not actually parse nested JSON objects
 * (every row threw "must carry a 'role'"); it was dead code until the Score Analyzer
 * integration (Phase B) wired the first real producer/consumer of the artefact. Jackson is
 * already on the classpath, so the parser is a thin tree walk.
 */
public final class JsonAiProposals {

    private static final int DEFAULT_PAGE = 1;
    private static final double DEFAULT_CONFIDENCE = 1.0d;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonAiProposals() {
    }

    /**
     * Parses the artefact content.
     *
     * @throws IllegalArgumentException not a JSON array/object, or a row without a usable role
     */
    public static List<AIProposals> parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty arrangement artefact");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("arrangement artefact is not valid JSON: " + e.getMessage());
        }
        List<AIProposals> proposals = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(row -> proposals.add(toProposal(row)));
        } else if (root.isObject()) {
            proposals.add(toProposal(root));
        } else {
            throw new IllegalArgumentException("arrangement artefact must be a JSON array of rows");
        }
        return proposals;
    }

    private static AIProposals toProposal(JsonNode row) {
        if (!row.isObject()) {
            throw new IllegalArgumentException("AI row must be a JSON object");
        }
        String role = "";
        int pageFrom = DEFAULT_PAGE;
        int pageTo = DEFAULT_PAGE;
        double confidence = DEFAULT_CONFIDENCE;
        String teamTag = "";
        for (Iterator<Map.Entry<String, JsonNode>> it = row.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            String key = field.getKey().trim().toUpperCase(Locale.ROOT);
            JsonNode value = field.getValue();
            switch (key) {
                case "ROLE":
                case "INSTRUMENT":
                case "NAME":
                    role = value.asText("").trim();
                    break;
                case "PAGEFROM":
                case "PAGE_FROM":
                case "FROM":
                    pageFrom = value.asInt(DEFAULT_PAGE);
                    break;
                case "PAGETO":
                case "PAGE_TO":
                case "TO":
                    pageTo = value.asInt(DEFAULT_PAGE);
                    break;
                case "CONFIDENCE":
                    confidence = value.asDouble(DEFAULT_CONFIDENCE);
                    break;
                case "TEAM_TAG":
                case "TEAMTAG":
                    teamTag = value.asText("").trim();
                    break;
                default:
                    break;
            }
        }
        if (role.isBlank()) {
            throw new IllegalArgumentException("AI row must carry a 'role'");
        }
        int from = Math.max(1, pageFrom);
        int to = Math.max(from, pageTo);
        double conf = Math.max(0.0d, Math.min(1.0d, confidence));
        return new AIProposals(role, from, to, conf, PartSource.AI, teamTag.isEmpty() ? null : teamTag);
    }
}
