package pl.michalbzowski.windband.application.command.scoreanalysis;

import pl.michalbzowski.windband.application.dto.scoreanalysis.AIProposals;
import pl.michalbzowski.windband.domain.composition.PartSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal, dependency-free reader for a {@code windband-ai} arrangement artefact: an array of
 * role proposals, each carrying a page range and a confidence.
 *
 * <ul>
 *   <li>Key order-insensitive, tolerant of loose key names a VLM might emit.</li>
 *   <li>Defaults (page 1, confidence 1.0) keep a slightly malformed artefact useful, but a row
 *       without a role still throws rather than maps to the wrong part.</li>
 * </ul>
 *
 * <p>There is no JSON library on the classpath (repo decision), so this is a small best-effort
 * parser. The goal is not full JSON compliance; it is to read the shape the runner is contracted
 * to emit without dragging a dependency into the pipeline.
 */
public final class JsonAiProposals {

    private static final int DEFAULT_PAGE = 1;
    private static final double DEFAULT_CONFIDENCE = 1.0d;

    private JsonAiProposals() {
    }

    public static List<AIProposals> parse(String json) {
        List<AIProposals> proposals = new ArrayList<>();
        for (String token : splitTopLevelArray(json.trim())) {
            Object[] pair = parsePair(token);
            if (pair != null) {
                proposals.add(toProposal(pair));
            }
        }
        return proposals;
    }

    private static AIProposals toProposal(Object[] kv) {
        if (!isTwoElementPair(kv)) {
            throw new IllegalArgumentException("AI row must be a key/value list");
        }
        String role = "";
        int pageFrom = DEFAULT_PAGE;
        int pageTo = DEFAULT_PAGE;
        double confidence = DEFAULT_CONFIDENCE;
        for (Object entry : kv) {
            if (!(entry instanceof Object[] pair)) {
                continue;
            }
            if (!(pair.length == 2)) {
                continue;
            }
            String key = String.valueOf(pair[0]).trim().toUpperCase(Locale.ROOT);
            String value = String.valueOf(pair[1]).trim();
            switch (key) {
                case "ROLE":
                    role = value;
                    break;
                case "PAGEFROM":
                case "PAGE_FROM":
                case "FROM":
                    pageFrom = applyPage(value, pageFrom);
                    break;
                case "PAGETO":
                case "PAGE_TO":
                case "TO":
                    pageTo = applyPage(value, pageTo);
                    break;
                case "CONFIDENCE":
                    confidence = applyConfidence(value, confidence);
                    break;
                default:
                    break;
            }
        }
        if (role.isBlank()) {
            throw new IllegalArgumentException("AI row must carry a 'role'");
        }
        return new AIProposals(
                role.trim(),
                Math.max(1, pageFrom),
                Math.max(pageFrom, pageTo),
                confidence,
                PartSource.AI);
    }

    private static int applyPage(String value, int fallback) {
        if (isInteger(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // keep fallback
                return fallback;
            }
        }
        return fallback;
    }

    private static double applyConfidence(String value, double fallback) {
        if (isDecimal(value)) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                // keep fallback
                return fallback;
            }
        }
        return fallback;
    }

    private static List<String> splitTopLevelArray(String json) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracket = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                bracket++;
            } else if (c == '}') {
                bracket--;
            }
            if (c == ',' && bracket == 0) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        List<String> trimmed = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) {
                trimmed.add(value);
            }
        }
        return trimmed;
    }

    private static Object[] parsePair(String token) {
        int colon = token.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String key = stripQuotes(token.substring(0, colon).trim());
        String value = stripQuotes(token.substring(colon + 1).trim());
        if (key.isEmpty() || value.isEmpty()) {
            return null;
        }
        String[] pair = {key, value};
        return pair;
    }

    private static boolean isTwoElementPair(Object v) {
        return v instanceof Object[] pair && pair.length == 2;
    }

    private static String stripQuotes(String s) {
        s = s.trim();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if (first == '"' && last == '"') {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static boolean isInteger(String raw) {
        String trimmed = raw.trim();
        int length = trimmed.length();
        boolean signed = trimmed.charAt(0) == '-';
        if (signed) {
            length = length - 1;
        }
        for (int i = 0; i < length; i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        return length > 0 || !signed;
    }

    private static boolean isDecimal(String raw) {
        String trimmed = raw.trim();
        return trimmed.indexOf('.') >= 0;
    }
}
