package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class DynamicGroupApiTest extends BaseIntegrationTest {

    @Autowired private GroupCommandService groupCmd;
    @Autowired private MemberAttributeCommandService attrCmd;
    @Autowired private GroupRepository groupRepo;
    @Autowired private MemberRepository memberRepo;
    @Autowired private BandRepository bandRepo;

    @Test
    void manualAddToDynamicGroup_throws() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        Member m = memberRepo.save(Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 1), band));
        Group g = groupRepo.findByDynamicSource(def).orElseThrow();
        assertThatThrownBy(() -> groupCmd.addMemberToGroup(g.getId(), m.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("grupy dynamicznej");
    }

    @Test
    void manualRemoveFromDynamicGroup_throws() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        Member m = memberRepo.save(Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 1), band));
        // First set the value to true so the member IS in the dynamic group
        attrCmd.setAttributeValue(m.getId(), def.getId(), "true");
        Group g = groupRepo.findByDynamicSource(def).orElseThrow();
        assertThatThrownBy(() -> groupCmd.removeMemberFromGroup(g.getId(), m.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("grupy dynamicznej");
    }

    private Band ensureBand() {
        return bandRepo.findById(1L).orElseGet(() -> {
            Band band = Band.create("Test Band for Dynamic Groups", "test-dynamic-groups");
            return bandRepo.save(band);
        });
    }
}
