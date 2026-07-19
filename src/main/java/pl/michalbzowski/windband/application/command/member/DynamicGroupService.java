package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberFieldSource;
import pl.michalbzowski.windband.domain.member.MemberRepository;

/**
 * Creates/synchronizes the fixed-field dynamic groups (e.g. "Aktywni" backed
 * by {@code Member.active}) across all bands. Extracted from
 * {@code DynamicGroupBackfillRunner} so it can be triggered both at startup
 * (via the runner, profile !test) and on demand (via an admin endpoint)
 * without depending on a profile-gated bean.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicGroupService {

    private final GroupCommandService groupCommandService;
    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;

    /**
     * Ensure every band has an "Aktywni" dynamic group backed by the fixed
     * member.active field, and sync its current membership. Idempotent.
     */
    public void ensureActiveGroupsForAllBands() {
        int activeGroups = 0;
        for (Band band : bandRepository.findAll()) {
            try {
                groupCommandService.createDynamicGroupForMemberField(MemberFieldSource.ACTIVE, band);
                for (Member m : memberRepository.findAllActiveByBandId(band.getId())) {
                    groupCommandService.syncMemberForActiveField(m);
                }
                activeGroups++;
            } catch (Exception e) {
                log.error("[backfill] Failed to ensure 'Aktywni' dynamic group for band {}: {}",
                        band.getId(), e.getMessage(), e);
            }
        }
        log.info("[backfill] Ensured 'Aktywni' dynamic groups for {} bands", activeGroups);
    }
}
