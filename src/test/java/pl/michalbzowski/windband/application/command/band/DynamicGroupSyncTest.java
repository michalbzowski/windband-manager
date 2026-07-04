package pl.michalbzowski.windband.application.command.band;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DynamicGroupSyncTest extends BaseIntegrationTest {

    @Autowired private MemberAttributeCommandService attrCmd;
    @Autowired private GroupRepository groupRepository;
    @Autowired private MemberAttributeDefRepository attrDefRepo;
    @Autowired private BandRepository bandRepo;
    @Autowired private MemberRepository memberRepository;

    @Test
    void creatingBooleanAttribute_spawnsDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);

        Optional<Group> g = groupRepository.findByDynamicSource(def);
        assertThat(g).isPresent();
        assertThat(g.get().getName()).isEqualTo("OSP");
        assertThat(g.get().isDynamic()).isTrue();
    }

    @Test
    void creatingTextAttribute_doesNotSpawnGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "Ksywka", "TEXT", false, false, 0, null);
        assertThat(groupRepository.findByDynamicSource(def)).isEmpty();
    }

    @Test
    void renamingAttribute_renamesDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        attrCmd.updateAttributeDef(def.getId(), "Ochotnicza", "BOOLEAN", false, false, 0, null);
        Group g = groupRepository.findByDynamicSource(def).orElseThrow();
        assertThat(g.getName()).isEqualTo("Ochotnicza");
    }

    @Test
    void deletingAttribute_deletesDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        attrCmd.deleteAttributeDef(def.getId());
        assertThat(groupRepository.findByDynamicSource(def)).isEmpty();
    }

    @Test
    void changingTypeAwayFromBoolean_deletesDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        attrCmd.updateAttributeDef(def.getId(), "OSP", "TEXT", false, false, 0, null);
        assertThat(groupRepository.findByDynamicSource(def)).isEmpty();
    }

    @Test
    void settingValueToTrue_addsMemberToGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        Member m = memberRepository.save(Member.create("Jan", "Kowalski", java.time.LocalDate.of(1990, 1, 1), band));
        attrCmd.setAttributeValue(m.getId(), def.getId(), "true");

        Group g = groupRepository.findByDynamicSource(def).orElseThrow();
        assertThat(g.getMemberCount()).isEqualTo(1);
        assertThat(g.getMembers().get(0).getMember().getId()).isEqualTo(m.getId());
    }

    @Test
    void changingValueFromTrueToFalse_removesMemberFromGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        Member m = memberRepository.save(Member.create("Jan", "Kowalski", java.time.LocalDate.of(1990, 1, 1), band));
        attrCmd.setAttributeValue(m.getId(), def.getId(), "true");
        attrCmd.setAttributeValue(m.getId(), def.getId(), "false");

        Group g = groupRepository.findByDynamicSource(def).orElseThrow();
        assertThat(g.getMemberCount()).isZero();
    }

    @Test
    void settingValueToFalse_directly_doesNotAddMember() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        Member m = memberRepository.save(Member.create("Jan", "Kowalski", java.time.LocalDate.of(1990, 1, 1), band));
        attrCmd.setAttributeValue(m.getId(), def.getId(), "false");

        Group g = groupRepository.findByDynamicSource(def).orElseThrow();
        assertThat(g.getMemberCount()).isZero();
    }

    private Band ensureBand() {
        return bandRepo.findById(1L).orElseGet(() -> {
            Band band = Band.create("Test Band for Dynamic Groups", "test-dynamic-groups");
            return bandRepo.save(band);
        });
    }
}
