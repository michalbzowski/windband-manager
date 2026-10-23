package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

/**
 * Faza D — resolves one AI-proposed part role (e.g. "Trąbka II" from the analyzer's
 * {@code arrangement.json}) onto the band's instruments and active members, using the same
 * matching rules the event distribution uses ({@link #normalize}d exact name match OR an
 * {@code instrument_role_map} row pairing the instrument's name with the role pattern).
 *
 * <p>Root (non-alias) instruments win over aliases for a given tier (US-1.2 semantics: the
 * canonical instrument is the one the library should standardize on); within a tier the band's
 * sort priority (repository list order) decides. A member "covers" the role when any of their
 * instruments does — mirroring {@code EventCompositionPartsQueryService.matchTagForPart}.
 */
@Service
@RequiredArgsConstructor
public class RoleInstrumentResolver {

    private final InstrumentRepository instrumentRepository;
    private final MemberRepository memberRepository;
    private final InstrumentRoleMapRepository roleMapRepository;

    /** A role resolution: the chosen band instrument (null when unresolved) + covering members. */
    public record Resolution(Instrument instrumentOrNull, List<String> memberNames) {
        public boolean isResolved() {
            return instrumentOrNull != null;
        }

        static Resolution unresolved() {
            return new Resolution(null, List.of());
        }
    }

    /** Lowercase + diacritics stripped — one shared normalization for names AND role labels. */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char ch = decomposed.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) {
                continue; // diacritic — dropped
            }
            sb.append(ch);
        }
        String base = sb.toString().toLowerCase(Locale.ROOT);
        return base.replaceAll("\\s\\s+", " "); // multiple spaces collapse to one (AI rows vary)
    }

    /** The band instrument an AI role maps to, or empty when the band has no match for it. */
    public Optional<Instrument> resolveInstrument(Long bandId, String roleLabel) {
        Instrument chosen = resolve(bandId, roleLabel).instrumentOrNull();
        return Optional.ofNullable(chosen);
    }

    /**
     * Full resolution of one AI role: instrument plus active members who would own it.
     * Never throws for an "unresolved" role (that is a legal AI outcome — the human reviews it).
     */
    public Resolution resolve(Long bandId, String roleLabel) {
        String target = normalize(roleLabel);
        if (target.isEmpty()) {
            return Resolution.unresolved();
        }
        List<InstrumentRoleMap> rows = roleMapRepository.findAllByBandId(bandId);

        Instrument chosen = null;
        int bestTier = Integer.MAX_VALUE;
        for (Instrument instrument : instrumentRepository.findAllOrderBySortPriorityByBandId(bandId)) {
            String nameN = normalize(instrument.getName());
            boolean match = nameN.equals(target)
                    || rows.stream().anyMatch(m ->
                    normalize(m.getSourceTag()).equals(nameN)
                            && normalize(m.getTargetRolePattern()).equals(target));
            if (!match) {
                continue;
            }
            int tier = instrument.getAliasOf() == null ? 0 : 1; // root beats alias
            if (tier < bestTier) {
                bestTier = tier;
                chosen = instrument;
            }
        }

        return new Resolution(chosen, memberNames(bandId, rows, target));
    }

    private List<String> memberNames(Long bandId, List<InstrumentRoleMap> rows, String target) {
        List<String> names = new ArrayList<>();
        for (Member member : memberRepository.findAllActiveByBandId(bandId)) {
            if (names.size() >= 50) { // sanity cap — a role should not own more than ~16 pages
                break;
            }
            boolean covers = member.getAllInstruments().stream().anyMatch(instrument ->
                    normalize(instrument.getName()).equals(target)
                    || rows.stream().anyMatch(m ->
                    normalize(m.getSourceTag()).equals(normalize(instrument.getName()))
                            && normalize(m.getTargetRolePattern()).equals(target)));
            if (covers) {
                String displayName = display(member);
                if (!displayName.isEmpty() && !names.contains(displayName)) {
                    names.add(displayName);
                }
            }
        }
        return List.copyOf(names);
    }

    private static String display(Member member) {
        String first = member.getFirstName() == null ? "" : member.getFirstName().trim();
        String last = member.getLastName() == null ? "" : member.getLastName().trim();
        return (first + " " + last).trim();
    }
}
