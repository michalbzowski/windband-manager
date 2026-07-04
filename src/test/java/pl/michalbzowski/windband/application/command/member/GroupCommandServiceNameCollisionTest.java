package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit-ish tests of {@link GroupCommandService#createDynamicGroupForAttribute}
 * covering the name-collision resolution path that was the root cause of the
 * 2026-07-04 production incident.
 * <p>
 * These tests are colocated with {@code DynamicGroupBackfillRunnerTest}, which tests
 * the same property through the full startup-runner path. This class targets the
 * service method directly, so it can assert on {@code resolveNameCollision}'s
 * " (2)", " (3)" … sequence in isolation.
 */
@Transactional
class GroupCommandServiceNameCollisionTest extends BaseIntegrationTest {

    @Autowired private GroupCommandService groupCmd;
    @Autowired private MemberAttributeCommandService attrCmd;
    @Autowired private GroupRepository groupRepository;
    @Autowired private BandRepository bandRepository;

    @Test
    void createDynamicGroup_noCollision_keepsBaseName() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "CleanName-" + suffix(), "BOOLEAN", false, false, 0, null);
        groupRepository.flush();

        Group g = groupRepository.findByDynamicSource(def).orElseThrow();
        assertThat(g.getName()).startsWith("CleanName-");
        assertThat(g.getName()).doesNotContain("(");
    }

    @Test
    void createDynamicGroup_singleCollision_appendsParen2() {
        Band band = ensureBand();
        String base = "Gosc-" + suffix();

        // Pre-existing manual group with the colliding name.
        groupRepository.save(new Group(base, "manual", band));
        groupRepository.flush();

        // BOOLEAN attribute that will try to claim the same name.
        MemberAttributeDef def = attrCmd.createAttributeDef(band, base, "BOOLEAN", false, false, 0, null);
        groupRepository.flush();

        // The manual group is untouched.
        assertThat(groupRepository.existsByNameAndBandId(base, band.getId())).isTrue();
        // The dynamic group is suffixed.
        Group dynamic = groupRepository.findByDynamicSource(def).orElseThrow();
        assertThat(dynamic.getName()).isEqualTo(base + " (2)");
    }

    @Test
    void createDynamicGroup_doubleCollision_appendsParen3() {
        Band band = ensureBand();
        String base = "Gosc3-" + suffix();

        // Pre-existing manual group with the colliding name, AND a pre-existing " (2)" group.
        groupRepository.save(new Group(base, "manual", band));
        groupRepository.save(new Group(base + " (2)", "manual-2", band));
        groupRepository.flush();

        MemberAttributeDef def = attrCmd.createAttributeDef(band, base, "BOOLEAN", false, false, 0, null);
        groupRepository.flush();

        Group dynamic = groupRepository.findByDynamicSource(def).orElseThrow();
        assertThat(dynamic.getName()).isEqualTo(base + " (3)");
    }

    @Test
    void createDynamicGroup_isIdempotent_secondCallReturnsSameGroup() {
        Band band = ensureBand();
        String base = "Idem-" + suffix();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, base, "BOOLEAN", false, false, 0, null);
        groupRepository.flush();

        Group first = groupRepository.findByDynamicSource(def).orElseThrow();

        // Second invocation must return the same group, NOT create a "Foo (2)" one.
        Group second = groupCmd.createDynamicGroupForAttribute(def);
        groupRepository.flush();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getName()).isEqualTo(base);
    }

    @Test
    void createDynamicGroup_doesNotPoisonOuterTransaction() {
        // This is the precise production-bug scenario: a single failure inside the
        // backfill loop must not cascade. We simulate it by directly calling the
        // service after a manual group with the colliding name has been created.
        // Before the fix, the inner @Transactional (class-level on GroupCommandService)
        // would mark the caller's transaction as rollback-only, and any subsequent
        // operation (e.g. another createDynamicGroupForAttribute in the same loop)
        // would throw "Transaction silently rolled back".
        Band band = ensureBand();

        String baseA = "TxA-" + suffix();
        String baseB = "TxB-" + suffix();

        // Pre-create manual collision for A only.
        groupRepository.save(new Group(baseA, "manual-A", band));
        groupRepository.flush();

        MemberAttributeDef defA = attrCmd.createAttributeDef(band, baseA, "BOOLEAN", false, false, 0, null);
        MemberAttributeDef defB = attrCmd.createAttributeDef(band, baseB, "BOOLEAN", false, false, 0, null);
        groupRepository.flush();

        // Both dynamic groups must exist. (defA suffixed, defB clean.)
        Group dynamicA = groupRepository.findByDynamicSource(defA).orElseThrow();
        Group dynamicB = groupRepository.findByDynamicSource(defB).orElseThrow();
        assertThat(dynamicA.getName()).isEqualTo(baseA + " (2)");
        assertThat(dynamicB.getName()).isEqualTo(baseB);
    }

    private Band ensureBand() {
        // Each test gets its own band with a unique slug so that name-collision
        // assertions are isolated from sibling tests. (We do not rely on the global
        // band with id=1 because the previous test in this class might have
        // populated that namespace.)
        return bandRepository.save(Band.create(
                "NameCollisionTest Band-" + UUID.randomUUID(),
                "name-collision-test-" + UUID.randomUUID()));
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
