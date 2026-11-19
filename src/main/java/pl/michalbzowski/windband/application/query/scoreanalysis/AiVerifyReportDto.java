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
        List<AiVerifyRow> rows) {

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
}
