package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.util.List;

/**
 * Payload widoku "raport spójności" (flagged feature): the AI's proposal rows resolved the same
 * way as the legacy preview, <b>plus</b> the global consistency verdicts from
 * {@link ProposalConsistency} and the score-file facts the UI needs for page previews.
 *
 * <p>Immutability: plain records so the Thymeleaf layer renders without caching state and the
 * controller can pass the object straight through (or serialize it — JSON-stable field names).
 */
public record AiVerifyReportDto(
        Long analysisId,
        Long fileId,
        Integer pageCount,
        ProposalsPreviewDto.WorkMeta work,
        List<ProposalsPreviewDto.InstrumentOption> instruments,
        boolean simulated,
        int errorRows,
        int warningRows,
        List<AiVerifyRow> rows,
        List<PageRange> existingPartRanges,
        List<ExcludedRange> excludedRanges) {

    /** Page range of one already-stored part of the composition (gap-coverage baseline). */
    public record PageRange(int pageFrom, int pageTo) {}

    /**
     * One "nie wykorzystam" mark visible in the report's marked-pages section:
     * scope + audit pair (who / when — last writer wins; "Przywróć" removes the row).
     */
    public record ExcludedRange(int pageFrom, int pageTo, String markedBy, String markedAt) {}

    /** Marked ranges as a null-safe list (template-safe getter convenience). */
    public List<ExcludedRange> excludedRangesOrEmpty() {
        return excludedRanges == null ? List.of() : excludedRanges;
    }

    /** Stable JSON for the boot script (numeric-only payload, safe for th:attr). */
    public String existingPartRangesJson() {
        char q = (char) 34;
        StringBuilder sb = new StringBuilder("[");
        List<PageRange> rs = existingPartRanges == null ? List.of() : existingPartRanges;
        for (int i = 0; i < rs.size(); i++) {
            PageRange r = rs.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{").append(q).append("from").append(q).append(":")
                    .append(r.pageFrom())
                    .append(",").append(q).append("to").append(q).append(":")
                    .append(r.pageTo()).append("}");
        }
        return sb.append("]").toString();
    }

    /**
     * Stable JSON of the marked ("nie wykorzystam") ranges for the boot script —
     * {@code [{from,to,by,at}, ...]} with string fields escaped (th:attr-safe).
     */
    public String excludedRangesJson() {
        char q = (char) 34;
        StringBuilder sb = new StringBuilder("[");
        List<ExcludedRange> rs = excludedRangesOrEmpty();
        for (int i = 0; i < rs.size(); i++) {
            ExcludedRange r = rs.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{")
                    .append(q).append("from").append(q).append(":").append(r.pageFrom())
                    .append(",").append(q).append("to").append(q).append(":").append(r.pageTo())
                    .append(",").append(q).append("by").append(q).append(":")
                    .append(jsonString(r.markedBy()))
                    .append(",").append(q).append("at").append(q).append(":")
                    .append(jsonString(r.markedAt() == null ? "" : r.markedAt()))
                    .append("}");
        }
        return sb.append("]").toString();
    }

    /** Minimal JSON string escape (backslash + quote) for the hand-rolled boot payload. */
    private static String jsonString(String value) {
        StringBuilder b = new StringBuilder(value.length() + 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                b.append('\\');
            }
            b.append(c);
        }
        return b.toString();
    }
    /**
     * One proposal row in the report, with the consistency verdicts that apply to it.
     * {@code issues} is empty when the row participates in no detected problem.
     */
    public record AiVerifyRow(
            String role,
            int pageFrom,
            int pageTo,
            double confidence,
            String teamTag,
            Long instrumentIdOrNull,
            String instrumentNameOrNull,
            List<String> memberNames,
            List<ProposalConsistency.Issue> issues) {

        /** First and last page of the range — the two pages the preview thumbnails show. */
        public int firstPage() {
            return pageFrom;
        }

        public int lastPage() {
            return pageTo;
        }

        public boolean hasError() {
            return issues.stream().anyMatch(i -> "ERROR".equals(i.severity()));
        }

        /** True when at least one consistency verdict applies (error or warning). */
        public boolean needsAttention() {
            return !issues.isEmpty();
        }
    }

    public int rowCount() {
        return rows.size();
    }

    /** All rows are clean — the accept button can carry a strong positive. */
    public boolean allClear() {
        return errorRows == 0 && warningRows == 0;
    }

    /** Gap-coverage baseline: pages already owned by stored parts (empty when unknown). */
    public List<PageRange> partRanges() {
        return existingPartRanges == null ? List.of() : existingPartRanges;
    }
}
