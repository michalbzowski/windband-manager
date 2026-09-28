package pl.michalbzowski.windband.application.dto.composition;

import java.util.Objects;

/**
 * US-5.1 read model: one composition role that a member's instrument tag resolves to, plus the
 * {@code instrument_role_map} source-tag row it was produced from.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code roleLabel} — the composition role label (the {@code target_role_pattern}, e.g.
 *       "Trąbka 1", "Kornet 1"). This is the value that feeds distribution lists (US-5.3) and the
 *       parts-panel auto-suggest (US-7.1).</li>
 *   <li>{@code matchedSourceTag} — the {@code source_tag} of the role-map row that produced this role,
 *       kept for traceability/UX (e.g. "Kornet" when a "Kornet → Kornet 1" mapping was applied, or
 *       "Trąbka" when the alias chain reached the canonical root's mapping).</li>
 * </ul>
 *
 * <p>Immutability: {@code String} is final; no collection/Date escape — nothing to expose to a mutator.
 */
public record ResolvedInstrumentRole(String roleLabel, String matchedSourceTag) {

    public ResolvedInstrumentRole {
        roleLabel = Objects.requireNonNull(roleLabel, "roleLabel required");
        if (roleLabel.isBlank()) {
            throw new IllegalArgumentException("roleLabel must not be blank");
        }
        // matchedSourceTag is provenance only — null is a legitimate value, blank is trimmed away.
        matchedSourceTag = matchedSourceTag == null || matchedSourceTag.isBlank() ? null : matchedSourceTag.trim();
    }
}
