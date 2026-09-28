package pl.michalbzowski.windband.application.query.composition;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.composition.ResolvedInstrumentRole;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * US-5.1 — alias-aware resolution of a member's instrument tag to the composition roles it covers.
 *
 * <p><b>What this solves.</b> V39's {@code instrument_role_map} rows are keyed by a free-form
 * {@code source_tag}. If a band calls its first seat "Kornet" but the score's role label is
 * "Trąbka 1", no mapping row joins them unless the manager typed exactly that tag. The US-1.2 alias
 * hierarchy exists to close that gap: an alias instrument ("Kornet") points at a canonical root
 * ("Trąbka"). This service resolves tags through that hierarchy on the read side by widening each
 * input tag to its <i>alias family</i> (itself, its canonical root, and every direct alias of that
 * root) before consulting {@code instrument_role_map}. Concretely: "Kornet" matches "Trąbka 1",
 * "Trąbka 2" <em>and</em> "Kornet 1".
 *
 * <p><b>Why the full family, not just the root.</b> Resolution is <i>symmetric</i>: an alias and its
 * canonical root are two names of one musical role. A member playing either name should claim any
 * role a manager mapped to either label; restricting the lookup would drop role rows keyed by the
 * other name. "In the same alias family" is the match — whichever label the manager typed, it counts.
 *
 * <p><b>Termination / bounds.</b> Only one band's instruments (bounded by the band catalogue) and one
 * band's role-map rows are ever loaded; the write path already forbids self-aliases, foreign-band
 * aliases and chains ({@link Instrument#setAliasOf}), so at read time the family of a 1-step alias is
 * {tag, root, aliases-of-root} and always terminates. Nothing here walks a graph — just set membership.
 *
 * <p><b>Case convention.</b> Tag names are compared case-insensitively (folded with {@link Locale#ROOT}),
 * mirroring the V39 unique index {@code lower(source_tag)} so the app layer and the DB never disagree.
 * Role labels keep their original spelling in results; dedup/sort use the folded form because "Trąbka 1"
 * and "trąbka 1" are one role from the distributor's point of view (cf. {@code composition_instruments}
 * lowercase uniqueness).
 *
 * <p><b>Determinism.</b> Sorted by folded role label then matched source tag — stable regardless of DB row order.
 *
 * <p><b>Failure posture.</b> Null/blank tag → empty list (nothing to resolve). Unknown band → the
 * {@link IllegalArgumentException} from {@code getRequiredBand} propagates (→ HTTP 400), fail-closed so no
 * cross-band read can follow. Band with no matching rows → empty list, a valid state (a tag may simply have
 * no roles mapped yet).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentRoleResolutionQueryService {

    private final BandQueryService bandQueryService;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentRoleMapRepository roleMapRepository;

    /**
     * Resolves a member's instrument tag to the composition roles it covers in {@code bandId}.
     *
     * @param bandId        the team/band whose instruments + role map define the resolution — required,
     *                      unknown ids fail closed (400 via {@link BandQueryService#getRequiredBand})
     * @param instrumentTag the member's instrument name as stored, e.g. "Kornet" or "Trąbka"; blank/null → empty
     * @return non-null, sorted list; entries carry both the role label and which tag row produced them
     */
    public List<ResolvedInstrumentRole> resolveRoles(Long bandId, String instrumentTag) {
        if (instrumentTag == null || instrumentTag.isBlank()) {
            return List.of();
        }
        bandQueryService.getRequiredBand(bandId); // unknown band → IAE (400); nothing below can cross a band

        String foldedInput = fold(instrumentTag.trim());
        List<Instrument> bandInstruments = instrumentRepository.findAllOrderBySortPriorityByBandId(bandId);
        Set<String> family = aliasFamilyTags(bandInstruments, foldedInput);

        MatchedRoleAccumulator accumulator = new MatchedRoleAccumulator();
        for (InstrumentRoleMap row : roleMapRepository.findAllByBandId(bandId)) {
            String sourceTag = row.getSourceTag() == null ? "" : row.getSourceTag().trim();
            if (sourceTag.isEmpty() || !family.contains(fold(sourceTag))) {
                continue;
            }
            String role = roleLabel(row);
            if (role.isBlank()) {
                continue; // defensive: the entity forbids blank roles, but a dirty row must not poison the list
            }
            accumulator.add(role, sourceTag);
        }
        return accumulator.sortedRoles();
    }

    /** Roles only — the flat shape US-5.3's distribution list consumes. Same inputs/semantics as {@link #resolveRoles}. */
    public List<String> resolveRoleNames(Long bandId, String instrumentTag) {
        return resolveRoles(bandId, instrumentTag).stream()
                .map(ResolvedInstrumentRole::roleLabel)
                .toList();
    }

    // ────────────────────────────────── internals ──────────────────────────────────

    /**
     * Collects matched roles deduplicated on the folded role label — "Trąbka 1" and "trąbka 1" from two map
     * rows are one part and must not be handed to a member twice. First writer wins; input order is stable
     * (the adapter sorts by sourceTag, then role) so the provenance tag recorded is deterministic.
     */
    private static final class MatchedRoleAccumulator {

        private final Map<String, ResolvedInstrumentRole> unique = new LinkedHashMap<>();

        void add(String roleLabel, String matchedTag) {
            unique.putIfAbsent(fold(roleLabel), new ResolvedInstrumentRole(roleLabel.trim(), matchedTag));
        }

        List<ResolvedInstrumentRole> sortedRoles() {
            return unique.values().stream()
                    .sorted((a, b) -> {
                        int byRole = fold(a.roleLabel()).compareTo(fold(b.roleLabel()));
                        if (byRole != 0) {
                            return byRole;
                        }
                        String ta = a.matchedSourceTag() == null ? "" : a.matchedSourceTag();
                        String tb = b.matchedSourceTag() == null ? "" : b.matchedSourceTag();
                        return fold(ta).compareTo(fold(tb));
                    })
                    .toList();
        }
    }

    /**
     * Alias family of {@code foldedInput}: the tag itself (always kept — a hand-typed "Kornet" row stays
     * reachable even when no such instrument exists), its canonical root when it is an alias (or itself
     * when it is the root), and every direct alias of that root. Output set is folded+trimmed; matchers use
     * exact membership from here on.
     */
    private static Set<String> aliasFamilyTags(List<Instrument> bandInstruments, String foldedInput) {
        Instrument input = null;
        for (Instrument instrument : bandInstruments) {
            if (instrument.getName() != null && fold(instrument.getName().trim()).equals(foldedInput)) {
                input = instrument;
                break;
            }
        }
        Set<String> family = new LinkedHashSet<>();
        family.add(foldedInput);

        if (input == null) {
            return Set.copyOf(family); // unknown tag: only its own role rows can match
        }

        Instrument root = input.isAlias() ? input.getCanonicalInstrument() : input;
        if (root == null || root.getName() == null) {
            return Set.copyOf(family); // alias pointer resolved to nothing — stay with the tag itself
        }
        String rootName = fold(root.getName().trim());
        family.add(rootName);

        // Every direct alias of the root joins the family (covers "Kornet" when input is "Trąbka", and any
        // further synonyms pointing at the same root). Linking by folded name rather than id works on
        // transient test entities and on lazy proxies alike — the name is unique per (band_id, name)
        // anyway.
        for (Instrument instrument : bandInstruments) {
            if (!instrument.isAlias() || instrument.getName() == null) {
                continue;
            }
            Instrument target = instrument.getCanonicalInstrument();
            if (target != null && target.getName() != null
                    && rootName.equals(fold(target.getName().trim()))) {
                family.add(fold(instrument.getName().trim()));
            }
        }
        return Set.copyOf(family);
    }

    private static String roleLabel(InstrumentRoleMap row) {
        return row.getTargetRolePattern() == null ? "" : row.getTargetRolePattern();
    }

    private static String fold(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
