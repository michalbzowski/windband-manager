package pl.michalbzowski.windband.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberFieldSource;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;

/**
 * On application startup, walks every existing BOOLEAN {@link MemberAttributeDef}
 * across all bands and ensures a corresponding dynamic {@code Group} exists.
 * <p>
 * This is the production data migration for bands that had BOOLEAN attributes
 * before the dynamic-groups feature shipped. After the first deploy, every such
 * attribute will have its dynamic group, so subsequent restarts are no-ops
 * ({@code ensureDynamicGroupExists} is idempotent).
 * <p>
 * <b>Transaction boundary:</b> deliberately NOT marked {@code @Transactional}.
 * Each call to {@code ensureDynamicGroupExists} runs in its own REQUIRES_NEW
 * transaction (configured on {@code GroupCommandService.createDynamicGroupForAttribute}),
 * so a single attribute that fails — for example, due to an unexpected DB conflict —
 * is logged and skipped without aborting startup or poisoning other attributes'
 * transactions. Wrapping the whole loop in one transaction would cascade one
 * attribute's failure into a Hibernate {@code AssertionFailure} and crash the app
 * (see production incident 2026-07-04).
 * <p>
 * Profile-gated with {@code !test} to keep the test data set deterministic — tests
 * build attributes explicitly via {@code createAttributeDef} and the runner would
 * otherwise race with the test's own assertions.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DynamicGroupBackfillRunner implements ApplicationRunner {

    private final MemberAttributeDefRepository attributeDefRepository;
    private final MemberAttributeCommandService memberAttributeCommandService;
    private final GroupCommandService groupCommandService;
    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<MemberAttributeDef> allBoolean = attributeDefRepository.findAll().stream()
                .filter(d -> "BOOLEAN".equals(d.getType()))
                .toList();
        log.info("[backfill] Found {} BOOLEAN member attributes; ensuring dynamic groups", allBoolean.size());

        int succeeded = 0;
        int failed = 0;
        for (MemberAttributeDef def : allBoolean) {
            try {
                // ensureDynamicGroupExists → groupCommandService.createDynamicGroupForAttribute
                // → runs in its own REQUIRES_NEW transaction. A failure here is isolated to
                // this attribute; the outer runner has no transaction of its own to poison.
                memberAttributeCommandService.ensureDynamicGroupExists(def);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("[backfill] Failed to ensure dynamic group for attribute {} ({}): {}",
                        def.getId(), def.getName(), e.getMessage(), e);
            }
        }
        log.info("[backfill] Done. succeeded={}, failed={}, total={}",
                succeeded, failed, allBoolean.size());

        // Ensure every band has an "Aktywni" dynamic group backed by the fixed
        // member.active field, and sync its current membership. Idempotent.
        int activeGroups = 0;
        for (Band band : bandRepository.findAll()) {
            try {
                groupCommandService.createDynamicGroupForMemberField(MemberFieldSource.ACTIVE, band);
                List<Member> activeMembers = memberRepository.findAllActiveByBandId(band.getId());
                for (Member m : activeMembers) {
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
