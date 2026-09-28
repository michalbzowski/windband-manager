package pl.michalbzowski.windband.application.query.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.Distribution;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PartAssignment;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PartView;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PieceDistribution;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PlayerForPart;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.UnassignedComposition;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.InstrumentRoleResolutionQueryService;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventComposition;
import pl.michalbzowski.windband.domain.event.EventCompositionRepository;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * US-6.2 — the concrete part list an event distributes to its musicians: for each setlist
 * piece and each part row, which active members play it, over what page range, from which ZIP
 * entry. Read-only CQRS service; every rendered value is scalar in the DTO ("Shape C"), so no
 * lazy association can leak past the transaction boundary into Thymeleaf or a future mailer.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventCompositionPartsQueryService {

    private static final Comparator<PartAssignment> PART_COMPARATOR =
            Comparator.<PartAssignment>comparingInt(PartAssignment::setlistPosition)
                    .thenComparing(a -> fold(a.role()))
                    .thenComparing(PartAssignment::memberName);

    private final BandQueryService bandQueryService;
    private final EventRepository eventRepository;
    private final EventCompositionRepository eventCompositionRepository;
    private final MemberRepository memberRepository;
    private final CompositionInstrumentRepository partRepository;
    private final InstrumentRoleResolutionQueryService roleResolution;

    /**
     * Builds the event's distribution in the context of {@code bandId}.
     *
     * <p>Guarantees: unknown event id → 404 on every path; foreign band → 409 (no other tenant's
     * data is ever loaded); unknown team id → 400; null team context → empty assignments with the
     * piece list still surfaced. Parts are read through ONE DB-fresh, composition-scoped query —
     * never via the parent's possibly-stale {@code mappedBy} collection.
     */
    public Distribution forEvent(Long eventId, Long bandId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId jest wymagane");
        }
        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (bandId != null) {
            bandQueryService.getRequiredBand(bandId); // unknown team → IAE → 400, nothing loaded yet
            Long eventBandId = event.getBand() == null ? null : event.getBand().getId();
            if (eventBandId != null && !eventBandId.equals(bandId)) {
                throw new IllegalStateException("Wydarzenie " + eventId + " nie należy do zespołu " + bandId);
            }
        }

        List<EventComposition> setlist = eventCompositionRepository.findAllByEventIdOrderByOrderInSetAsc(eventId);
        // DB-fresh part rows for the whole setlist — one query; drives both the assignment and the
        // unassigned checks (the parent collection is deliberately never trusted here).
        Map<Long, List<CompositionInstrument>> partsByPiece = loadParts(setlist);
        if (bandId == null || setlist.isEmpty()) {
            // No active roster to resolve against — surface every part row as still-uncovered and
            // every part-less piece, so the UI never pretends the distribution is complete.
            List<PieceDistribution> pieces = pieceViews(setlist, partsByPiece, Map.of());
            return new Distribution(pieces, List.of(), unassigned(setlist, partsByPiece));
        }

        List<Member> members = memberRepository.findAllActiveByBandId(bandId);

        // Request-local memo: one US-5.1 resolution per distinct tag, for this call only (US-5.2 seam).
        Map<String, Set<String>> rolesByTag = new HashMap<>();
        List<PartAssignment> assignments = new ArrayList<>();
        Map<Long, List<PlayerForPart>> playersByPart = new LinkedHashMap<>();

        for (EventComposition ec : setlist) {
            Long pieceId = ec.getComposition().getId();
            int position = ec.getOrderInSet();
            for (CompositionInstrument part : partsByPiece.getOrDefault(pieceId, List.of())) {
                for (Member member : members) {
                    String matchedTag = matchTagForPart(bandId, rolesByTag, part, member);
                    if (matchedTag != null) {
                        playersByPart.computeIfAbsent(part.getId(), k -> new ArrayList<>())
                                .add(new PlayerForPart(member.getId(), memberName(member),
                                        member.isEmailConsentGiven()));
                        assignments.add(new PartAssignment(
                                member.getId(),
                                memberName(member),
                                member.isEmailConsentGiven(),
                                pieceId,
                                ec.getComposition().getTitle(),
                                position,
                                part.getId(),
                                part.getInstrumentRole().trim(),
                                part.getPageFrom(),
                                part.getPageTo(),
                                part.getFileRef(),
                                part.getVerifiedAt() != null,
                                matchedTag));
                    }
                }
            }
        }

        return new Distribution(
                pieceViews(setlist, partsByPiece, playersByPart),
                assignments.stream().sorted(PART_COMPARATOR).toList(),
                unassigned(setlist, partsByPiece));
    }

    /** One {@link PieceDistribution} per setlist position; every part row keeps its players (may be empty = uncovered). */
    private static List<PieceDistribution> pieceViews(List<EventComposition> setlist,
                                                      Map<Long, List<CompositionInstrument>> partsByPiece,
                                                      Map<Long, List<PlayerForPart>> playersByPart) {
        List<PieceDistribution> out = new ArrayList<>();
        for (EventComposition ec : setlist) {
            Long pieceId = ec.getComposition().getId();
            List<PartView> parts = new ArrayList<>();
            for (CompositionInstrument part : partsByPiece.getOrDefault(pieceId, List.of())) {
                parts.add(new PartView(
                        part.getId(),
                        part.getInstrumentRole() == null ? "?" : part.getInstrumentRole().trim(),
                        part.getPageFrom(),
                        part.getPageTo(),
                        part.getFileRef(),
                        part.getVerifiedAt() != null,
                        new ArrayList<>(playersByPart.getOrDefault(part.getId(), List.of()))));
            }
            out.add(new PieceDistribution(pieceId, ec.getComposition().getTitle(), ec.getOrderInSet(), parts));
        }
        return out;
    }

    // ────────────────────────────── internals ──────────────────────────────

    /** One DB-fresh query across all setlist pieces, grouped by composition id (stable DB order preserved). */
    private Map<Long, List<CompositionInstrument>> loadParts(List<EventComposition> setlist) {
        Set<Long> pieceIds = new java.util.LinkedHashSet<>();
        for (EventComposition ec : setlist) {
            Long id = ec.getComposition().getId();
            if (id != null) {
                pieceIds.add(id);
            }
        }
        Map<Long, List<CompositionInstrument>> byPiece = new HashMap<>();
        for (CompositionInstrument part : partRepository.findAllByCompositionIdIn(pieceIds)) {
            Long id = part.getComposition() == null ? null : part.getComposition().getId();
            if (id != null) {
                byPiece.computeIfAbsent(id, k -> new ArrayList<>()).add(part);
            }
        }
        return byPiece;
    }

    /**
     * Returns the first of {@code member}'s instrument tags that covers the part's role, or null.
     * Exact-name match is tried first (free-form US-7.1 roles route with no resolver cost); the
     * US-5.1 alias-family rule widens through the band's instrument_role_map rows.
     */
    private String matchTagForPart(Long bandId, Map<String, Set<String>> rolesByTag,
                                   CompositionInstrument part, Member member) {
        String partRole = part.getInstrumentRole();
        if (partRole == null || partRole.isBlank()) {
            return null; // a dirty row must not crash the distribution list
        }
        String foldedRole = fold(partRole.trim());

        List<String> tags = member.getAllInstruments().stream()
                .map(i -> i.getName())
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        for (String tag : tags) { // rule 1 — exact role-name match
            if (fold(tag).equals(foldedRole)) {
                return tag;
            }
        }
        for (String tag : tags) { // rule 2 — alias family via US-5.1 role map
            Set<String> covered = rolesByTag.computeIfAbsent(fold(tag), t -> new java.util.LinkedHashSet<>(
                    roleResolution.resolveRoleNames(bandId, tag).stream()
                            .filter(r -> r != null && !r.isBlank())
                            .map(r -> fold(r.trim()))
                            .toList()));
            if (covered.contains(foldedRole)) {
                return tag;
            }
        }
        return null;
    }

    /** Setlist positions with no part rows yet (US-3.03 gate) — surfaced, never hidden. */
    private static List<UnassignedComposition> unassigned(List<EventComposition> setlist,
                                                          Map<Long, List<CompositionInstrument>> partsByPiece) {
        List<UnassignedComposition> out = new ArrayList<>();
        for (EventComposition ec : setlist) {
            if (partsByPiece.getOrDefault(ec.getComposition().getId(), List.of()).isEmpty()) {
                out.add(new UnassignedComposition(
                        ec.getComposition().getId(),
                        ec.getComposition().getTitle(),
                        ec.getOrderInSet()));
            }
        }
        return out;
    }

    private static String memberName(Member member) {
        String first = member.getFirstName() == null ? "" : member.getFirstName().trim();
        String last = member.getLastName() == null ? "" : member.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? "Członek #" + member.getId() : name;
    }

    private static String fold(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
