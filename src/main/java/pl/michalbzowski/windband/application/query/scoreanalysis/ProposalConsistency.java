package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Consistency checks over the AI's part→page proposal, computed <b>before</b> acceptance so the
 * human sees global mistakes (overlapping voices, page gaps, ranges beyond the score file)
 * instead of discovering them after parts are created.
 *
 * <p>Deliberately pure: no Spring, no repositories — a static function over an immutable row
 * list, so the rules are trivially unit-testable and mutation-testable. The UI layer decides
 * presentation; this class decides only <em>what is wrong and why</em>.
 *
 * <h3>Issue codes (stable identifiers, UI text in Polish)</h3>
 * <ul>
 *   <li>{@code OVERLAP} (error) — the range intersects another voice's range; the two would
 *       claim some pages each for a different part;</li>
 *   <li>{@code BEYOND_PAGES} (error) — {@code pageTo} exceeds the score file's page count;</li>
 *   <li>{@code GAP} (warning) — pages between two consecutive (in start order) ranges are covered
 *       by no voice; a gap before the first range is legal (title/dedication pages) and NOT
 *       reported;</li>
 *   <li>{@code UNMAPPED} (warning) — the band roster has no instrument for this role.</li>
 * </ul>
 */
public final class ProposalConsistency {

    private ProposalConsistency() {
        // stateless utility
    }

    /** One proposal row as consistency input. */
    public record Row(String role, int pageFrom, int pageTo, boolean mapped) {}

    /** One detected problem for one row; {@code severity} is "ERROR" or "WARN". */
    public record Issue(String code, String severity, String detail) {}

    /** Per-row issues (aligns with the input list order) + how many rows need attention per severity. */
    public record Report(List<List<Issue>> issues, int errorRows, int warningRows) {

        public int rowCount() {
            return issues.size();
        }
    }

    /**
     * Runs all checks.
     *
     * @param rows the AI proposal rows in their natural order (1-based ranges, {@code pageFrom <= pageTo})
     * @param pageCount total pages of the analysed score file; {@code null} disables the beyond-pages check
     * @return per-row issues; empty input yields an empty report
     */
    public static Report check(List<Row> rows, Integer pageCount) {
        int n = rows == null ? 0 : rows.size();
        List<List<Issue>> perRow = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            perRow.add(new ArrayList<>());
        }
        if (n == 0) {
            return new Report(List.of(), 0, 0);
        }

        // OVERLAP — pairwise intersection; both rows get the issue, naming the other voice.
        for (int i = 0; i < n; i++) {
            Row a = rows.get(i);
            for (int j = i + 1; j < n; j++) {
                Row b = rows.get(j);
                int lo = Math.max(a.pageFrom(), b.pageFrom());
                int hi = Math.min(a.pageTo(), b.pageTo());
                if (lo <= hi) {
                    perRow.get(i).add(new Issue("OVERLAP", "ERROR",
                            "Nakłada się z głosem " + label(b.role()) + " (str. " + b.pageFrom() + "–" + b.pageTo() + ")."));
                    perRow.get(j).add(new Issue("OVERLAP", "ERROR",
                            "Nakłada się z głosem " + label(a.role()) + " (str. " + a.pageFrom() + "–" + a.pageTo() + ")."));
                }
            }
        }

        // BEYOND_PAGES — only when the file's page count is known.
        if (pageCount != null) {
            for (int i = 0; i < n; i++) {
                Row r = rows.get(i);
                if (r.pageTo() > pageCount) {
                    perRow.get(i).add(new Issue("BEYOND_PAGES", "ERROR",
                            "Zakres kończy się na str. " + r.pageTo() + ", a plik ma tylko " + pageCount + " stron."));
                }
            }
        }

        // GAP — interior gaps between consecutive ranges (start order). The leading part of the
        // file may be front matter, so a gap before the first range is intentionally not flagged.
        List<Integer> starts = new ArrayList<>();
        for (Row r : rows) {
            starts.add(r.pageFrom());
        }
        starts.sort(Integer::compareTo);
        int prevTo = -1;
        for (int start : starts) {
            if (prevTo >= 0 && start > prevTo + 1) {
                int gapFrom = prevTo + 1;
                int gapTo = start - 1;
                String detail = "Przed tym głosem luka: str. " + gapFrom + "–" + gapTo
                        + " nie należy do żadnego głosu.";
                for (int i = 0; i < n; i++) {
                    if (rows.get(i).pageFrom() == start) {
                        perRow.get(i).add(new Issue("GAP", "WARN", detail));
                    }
                }
            }
            prevTo = Math.max(prevTo, endOf(rows, start));
        }

        // UNMAPPED — the roster has no voice for this role.
        for (int i = 0; i < n; i++) {
            if (!rows.get(i).mapped()) {
                perRow.get(i).add(new Issue("UNMAPPED", "WARN",
                        "Brak głosu z zespołu dla tej roli — wybierz instrument przed akceptacją."));
            }
        }

        int errorRows = 0;
        int warningRows = 0;
        for (List<Issue> issues : perRow) {
            boolean hasError = false;
            boolean hasWarn = false;
            for (Issue issue : issues) {
                if ("ERROR".equals(issue.severity())) {
                    hasError = true;
                } else {
                    hasWarn = true;
                }
            }
            if (hasError) {
                errorRows++;
            } else if (hasWarn) {
                warningRows++;
            }
        }

        List<List<Issue>> stable = new ArrayList<>(n);
        for (List<Issue> issues : perRow) {
            stable.add(List.copyOf(issues));
        }
        return new Report(stable, errorRows, warningRows);
    }

    private static String label(String role) {
        return role == null || role.isBlank() ? "nieznanym głosem" : role;
    }

    private static int endOf(List<Row> rows, int start) {
        int max = start;
        for (Row r : rows) {
            if (r.pageFrom() == start && r.pageTo() > max) {
                max = r.pageTo();
            }
        }
        return max;
    }
}
