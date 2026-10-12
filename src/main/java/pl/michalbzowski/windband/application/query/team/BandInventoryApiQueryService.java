package pl.michalbzowski.windband.application.query.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberInstrument;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only query service backing the v1 integration API consumed by the
 * windband-inventory app (frozen contract B1/B5/B8/B10 — see
 * windband-inventory: docs/w1-analiza-biznesowa-2026-10-12.md).
 *
 * <p>Rules enforced here (per the contract):
 * <ul>
 *   <li>Band roles come from the WM database ({@code UserTeamRole}/{@code TeamRole}),
 *       never from OIDC claims.</li>
 *   <li>A requesting user may read a band's roster only when they hold any
 *       membership in that band; otherwise {@link NotBandMemberException} (403).</li>
 *   <li>The member roster carries the expected set — the member's instruments
 *       (name + primary flag), primary instrument first, then by band instrument
 *       order and name.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BandInventoryApiQueryService {

    private final TeamQueryService teamQueryService;
    private final MemberRepository memberRepository;
    private final BandRepository bandRepository;

    /** View of a band with the requesting user's role in it (ADMIN or MEMBER). */
    public record BandView(Long bandId, String name, String role) {}

    /** One entry of a member's expected set (their assigned instrument). */
    public record ExpectedEntry(String instrument, boolean primary) {}

    /** A band roster member with their expected set; {@code active} mirrors WM's
     *  member status so inventory can filter inactive members client-side. */
    public record MemberView(Long memberId, String firstName, String lastName,
                             boolean active, List<ExpectedEntry> expectedSet) {}

    /**
     * Thrown when the requesting user has no membership in the target band —
     * mapped to HTTP 403 by {@code GlobalExceptionHandler}.
     */
    public static class NotBandMemberException extends RuntimeException {
        private final Long bandId;

        public NotBandMemberException(Long bandId) {
            super("User is not a member of band " + bandId);
            this.bandId = bandId;
        }

        public Long getBandId() {
            return bandId;
        }
    }

    /**
     * GET /api/v1/me/bands — all bands the requesting user belongs to, with their
     * per-band role. Sorted by band name (TeamQueryService contract).
     */
    public List<BandView> myBands(Long userId) {
        return teamQueryService.getUserTeams(userId).stream()
                .map(team -> new BandView(team.id(), team.name(), team.role()))
                .toList();
    }

    /**
     * GET /api/v1/bands/{bandId}/members — the band roster (active and inactive,
     * each flagged) with each member's expected set. 403 for foreign users.
     */
    public List<MemberView> bandMembersWithExpectedSet(Long requestingUserId, Long bandId) {
        if (teamQueryService.getUserTeam(requestingUserId, bandId).isEmpty()) {
            throw new NotBandMemberException(bandId);
        }
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + bandId));
        return memberRepository.findAllByBandOrderByLastNameAscFirstNameAsc(band).stream()
                .map(this::toMemberView)
                .toList();
    }

    /** Maps the aggregate to its DTO inside the read transaction (lazy collections). */
    private MemberView toMemberView(Member member) {
        List<ExpectedEntry> expectedSet = member.getInstruments().stream()
                .sorted(Comparator
                        .comparing((MemberInstrument mi) -> !mi.isPrimary())
                        .thenComparing(mi -> mi.getInstrument().getSortPriority())
                        .thenComparing(mi -> mi.getInstrument().getName()))
                .map(mi -> new ExpectedEntry(mi.getInstrument().getName(), mi.isPrimary()))
                .toList();
        return new MemberView(member.getId(), member.getFirstName(), member.getLastName(),
                member.isActive(), expectedSet);
    }
}
