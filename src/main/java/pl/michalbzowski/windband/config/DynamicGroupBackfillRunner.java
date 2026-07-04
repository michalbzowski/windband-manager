package pl.michalbzowski.windband.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;

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
 * Profile-gated with {@code !test} to keep the test data set deterministic —
 * tests build attributes explicitly via {@code createAttributeDef} and the
 * runner would otherwise race with the test's own assertions.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DynamicGroupBackfillRunner implements ApplicationRunner {

    private final MemberAttributeDefRepository attributeDefRepository;
    private final MemberAttributeCommandService memberAttributeCommandService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<MemberAttributeDef> allBoolean = attributeDefRepository.findAll().stream()
                .filter(d -> "BOOLEAN".equals(d.getType()))
                .toList();
        log.info("[backfill] Found {} BOOLEAN member attributes; ensuring dynamic groups", allBoolean.size());
        for (MemberAttributeDef def : allBoolean) {
            try {
                memberAttributeCommandService.ensureDynamicGroupExists(def);
            } catch (Exception e) {
                log.error("[backfill] Failed to ensure dynamic group for attribute {}: {}", def.getId(), e.getMessage(), e);
            }
        }
    }
}
