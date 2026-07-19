package pl.michalbzowski.windband.domain.member;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamicGroupSourceTest {

    private static final Band BAND = Band.create("Test Band", "test-band");

    private Member activeMember() {
        Member m = Member.create("Anna", "Active", LocalDate.now().minusYears(20), BAND);
        return m; // Member.create sets active=true
    }

    private Member inactiveMember() {
        Member m = Member.create("Tom", "Inactive", LocalDate.now().minusYears(20), BAND);
        m.deactivate();
        return m;
    }

    @Test
    void memberFieldSource_active_matchesOnlyActiveMembers() {
        MemberFieldSource source = new MemberFieldSource(MemberFieldSource.ACTIVE);

        assertThat(source.getName()).isEqualTo("Aktywni");
        assertThat(source.memberMatches(activeMember())).isTrue();
        assertThat(source.memberMatches(inactiveMember())).isFalse();
    }

    @Test
    void attributeDefSource_matchesWhenValueIsTrue() {
        MemberAttributeDef def = MemberAttributeDef.create(BAND, "Gość", "BOOLEAN", false, false, 0, null);
        Member member = activeMember();

        MemberAttributeValueRepository repo = mock(MemberAttributeValueRepository.class);
        when(repo.findByMemberAndAttributeDef(member, def))
                .thenReturn(Optional.of(MemberAttributeValue.create(member, def, "true")));

        AttributeDefSource source = new AttributeDefSource(def, repo);
        assertThat(source.getName()).isEqualTo("Gość");
        assertThat(source.memberMatches(member)).isTrue();
    }

    @Test
    void attributeDefSource_doesNotMatchWhenValueIsFalseOrAbsent() {
        MemberAttributeDef def = MemberAttributeDef.create(BAND, "Gość", "BOOLEAN", false, false, 0, null);
        Member member = activeMember();

        MemberAttributeValueRepository repo = mock(MemberAttributeValueRepository.class);
        when(repo.findByMemberAndAttributeDef(member, def))
                .thenReturn(Optional.of(MemberAttributeValue.create(member, def, "false")));

        assertThat(new AttributeDefSource(def, repo).memberMatches(member)).isFalse();

        when(repo.findByMemberAndAttributeDef(member, def)).thenReturn(Optional.empty());
        assertThat(new AttributeDefSource(def, repo).memberMatches(member)).isFalse();
    }
}
