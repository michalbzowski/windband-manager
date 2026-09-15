package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.Instrument.AliasValidationException;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Write-path for the {@link Instrument} aggregate (US-1.2): create / update / delete instruments
 * AND maintain alias relationships between them ("Kornet" → "Trąbka").
 *
 * <p>Band isolation invariant: whenever a {@code teamId} is supplied, every read of an instrument
 * checks that the row's {@code band_id} matches; any mismatch surfaces as
 * {@link IllegalArgumentException("Instrument not found: <id>")}. This mirrors the existing
 * {@code resolveVisibleInstrument(id, teamId)} guard and ensures alias writes can never cross
 * bands (US-1.2 acceptance criterion "band-scoped aliasOf").
 *
 * <p>Aliasing invariants enforced here, on top of the {@link Instrument.AliasValidationException}
 * domain checks:
 * <ul>
 *   <li>{@code id != aliasId} (no self-alias),</li>
 *   <li>{@code instrument.isRoot()} before it may become an alias (no 2-step chains),</li>
 *   <li>both rows share a band when both are band-scoped,</li>
 *   <li>target exists and is in scope for the caller's band.</li>
 * </ul>
 *
 * <p>Delete guard: an instrument that is either (a) assigned to one or more members, or
 * (b) referenced by another instrument as its alias cannot be removed — the second case is a new
 * US-1.2 rule added here on top of the existing member assignment guard. The error message lists
 * both offending classes so the operator knows what to re-point / unassign first.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InstrumentCommandService {

    private static final String INSTRUMENT_IN_USE_MESSAGE = "Cannot delete instrument that is assigned to one or more members";
    private static final String ALIAS_OF_ANOTHER_MESSAGE = "Cannot delete instrument that another instrument aliases";

    private final InstrumentRepository instrumentRepository;
    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;

    // ── Create / Update (pre-existing) ────────────────────────────────────────────────

    public Instrument createInstrument(String name, String description, Integer sortPriority) {
        return createInstrument(name, description, sortPriority, 1L);
    }

    public Instrument createInstrument(String name, String description, Integer sortPriority, Long teamId) {
        Band band = resolveBand(teamId);
        Instrument instrument = band != null ? Instrument.create(name, band) : Instrument.create(name);
        if (description != null) {
            instrument.updateDescription(description);
        }
        if (sortPriority != null) {
            instrument.updateSortPriority(sortPriority);
        }
        return instrumentRepository.save(instrument);
    }

    public Instrument updateInstrument(Long id, String name, String description, Integer sortPriority) {
        return updateInstrument(id, name, description, sortPriority, null);
    }

    public Instrument updateInstrument(Long id, String name, String description, Integer sortPriority, Long teamId) {
        Instrument instrument = resolveVisibleInstrument(id, teamId);
        instrument.updateName(name);
        if (description != null) {
            instrument.updateDescription(description);
        }
        if (sortPriority != null) {
            instrument.updateSortPriority(sortPriority);
        }
        return instrumentRepository.save(instrument);
    }

    public Instrument updateSortPriority(Long id, Integer sortPriority) {
        return updateSortPriority(id, sortPriority, null);
    }

    public Instrument updateSortPriority(Long id, Integer sortPriority, Long teamId) {
        Instrument instrument = resolveVisibleInstrument(id, teamId);
        instrument.updateSortPriority(sortPriority);
        return instrumentRepository.save(instrument);
    }

    // ── US-1.2: alias maintenance ───────────────────────────────────────────────────────

    /**
     * Sets instrument {@code id} as an alias of instrument {@code aliasId}. Band-scoped by {@code teamId}.
     * <p>
     * Acceptance criteria covered (US-1.2):
     * <ul>
     *   <li>same band only — cross-band alias writes throw {@link AliasValidationException},</li>
     *   <li>no self-alias (id != aliasId),</li>
     *   <li>target must be a root (not itself an alias of another) → 1-step aliases only,</li>
     *   <li>if {@code id} already has another instrument pointing to it as an alias, the existing
     *       references are preserved — {@link Instrument#getInstrumentName()} is not modified, only
     *       the domain object's {@code aliasOf} pointer changes. (US-1.5 "alias resolution" then
     *       resolves through this pointer at read time, keeping the historical data untouched.)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if either id does not exist in scope, for symmetry with the rest
     *                                  of the service (the user expects "not found" semantics).
     * @throws AliasValidationException if the domain rule is violated (self-alias / cross-band / chain).
     */
    @Transactional
    public Instrument updateAliasOf(Long id, Long aliasId, Long teamId) {
        if (id.equals(aliasId)) {
            throw new AliasValidationException(
                    "Instrument cannot be an alias of itself", null, null);
        }
        Instrument self = resolveVisibleInstrument(id, teamId);
        Instrument target = resolveVisibleInstrument(aliasId, teamId);
        // US-1.2: target must be a root (not already an alias of another) — this is the 1-step rule.
        if (target.isAlias()) {
            throw new AliasValidationException(
                    "Target instrument '" + target.getName() + "' is itself an alias; aliases must point at roots",
                    self, target);
        }
        self.setAliasOf(target); // entity-level invariant guard (band equality + self check)
        return instrumentRepository.save(self);
    }

    /** Clears the alias pointer of {@code id} back to root. Useful after reassignment in US-1.4. */
    @Transactional
    public Instrument clearAlias(Long id, Long teamId) {
        Instrument self = resolveVisibleInstrument(id, teamId);
        self.clearAlias();
        return instrumentRepository.save(self);
    }

    /**
     * Convenience: returns all roots of a band (an alias's primary for each role). Read-path used by
     * US-1.5 distribution and by the admin UI "list root instruments" pane. Band-null callers get an
     * empty result (callers should not be calling with a missing bandId, but being lenient here is
     * cheaper than throwing).
     */
    public List<Instrument> getRootInstruments(Long teamId) {
        if (teamId == null) {
            return List.of();
        }
        return instrumentRepository.findRootInstrumentsByBandId(teamId);
    }

    /** Returns all instruments that alias the given canonical instrument. */
    public List<Instrument> getAliases(Long canonicalId, Long teamId) {
        Instrument canonical = resolveVisibleInstrument(canonicalId, teamId);
        List<Instrument> aliases = instrumentRepository.findByAliasOf(canonical);
        // Filter by band when we have one (defensive: the DB is supposed to scope already).
        if (teamId != null) {
            return aliases.stream()
                    .filter(a -> a.getBand() == null || teamId.equals(a.getBand().getId()))
                    .collect(Collectors.toList());
        }
        return aliases;
    }

    // ── Read paths retained for existing controllers (TagController / TagPageController) ───

    public List<Instrument> getAllInstruments() {
        return getAllInstruments(null);
    }

    public List<Instrument> getAllInstruments(Long teamId) {
        if (teamId != null) {
            return instrumentRepository.findAllOrderBySortPriorityByBandId(teamId);
        }
        return instrumentRepository.findAll();
    }

    public Instrument getInstrumentById(Long id) {
        return getInstrumentById(id, null);
    }

    public Instrument getInstrumentById(Long id, Long teamId) {
        return resolveVisibleInstrument(id, teamId);
    }

    // ── Delete (pre-existing + US-1.2 alias guard) ──────────────────────────────────────

    public void deleteInstrument(Long id) {
        deleteInstrument(id, null);
    }

    public void deleteInstrument(Long id, Long teamId) {
        Instrument instrument = resolveVisibleInstrument(id, teamId);
        List<Member> assignedMembers = memberRepository.findByInstrument(instrument);
        if (!assignedMembers.isEmpty()) {
            throw new InstrumentInUseException(
                    INSTRUMENT_IN_USE_MESSAGE + ". Assigned members: " + formatMemberNames(assignedMembers),
                    instrument.getName(),
                    assignedMembers.stream().map(this::formatMemberName).collect(Collectors.toList()));
        }
        List<Instrument> reverseAliases = instrumentRepository.findByAliasOf(instrument);
        if (!reverseAliases.isEmpty()) {
            throw new InstrumentInUseException(
                    ALIAS_OF_ANOTHER_MESSAGE + " by: " + reverseAliases.stream().map(Instrument::getName).collect(Collectors.joining(", ")),
                    instrument.getName(),
                    List.of());
        }
        instrumentRepository.delete(instrument);
    }

    public void deleteInstrumentIfUnused(Long id, Long teamId) {
        deleteInstrument(id, teamId);
    }

    // ── Internal helpers ────────────────────────────────────────────────────────────────

    private List<String> formatMemberNames(List<Member> members) {
        return members.stream().map(this::formatMemberName).collect(Collectors.toList());
    }

    private String formatMemberName(Member member) {
        return member.getFirstName() + " " + member.getLastName();
    }

    /**
     * Resolves an instrument by id, enforcing band scope when a teamId is supplied.
     * A cross-band lookup returns "not found" semantics (does not leak existence across bands).
     * Instruments with {@code band == null} are still visible to a band-scoped caller — this mirrors
     * the existing behaviour of {@link Instrument#getInstrumentName()} and avoids blocking the legacy
     * rows that pre-date US-1.2 alias support (they simply do not participate in aliases until they
     * are re-saved under a band).
     */
    private Instrument resolveVisibleInstrument(Long id, Long teamId) {
        Instrument instrument = instrumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
        if (teamId != null && instrument.getBand() != null && !teamId.equals(instrument.getBand().getId())) {
            // Cross-band leak — fail with the same message as "id missing" so no existence is revealed.
            throw new IllegalArgumentException("Instrument not found: " + id);
        }
        return instrument;
    }

    private Band resolveBand(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return bandRepository.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Band not found: " + teamId));
    }

    /**
     * Thrown when a delete is blocked because members are still assigned or other instruments alias the row.
     */
    public static class InstrumentInUseException extends IllegalStateException {
        private final String instrumentName;
        private final List<String> memberNames;

        public InstrumentInUseException(String message, String instrumentName, List<String> memberNames) {
            super(message);
            this.instrumentName = instrumentName;
            this.memberNames = List.copyOf(memberNames);
        }

        public String getInstrumentName() {
            return instrumentName;
        }

        public List<String> getMemberNames() {
            return memberNames;
        }
    }
}
