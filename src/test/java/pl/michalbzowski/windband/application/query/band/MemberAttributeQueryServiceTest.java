package pl.michalbzowski.windband.application.query.band;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAttributeQueryServiceTest {

    @Mock
    private MemberAttributeDefRepository attributeDefRepository;
    @Mock
    private MemberAttributeValueRepository attributeValueRepository;
    @InjectMocks
    private MemberAttributeQueryService service;

    @Test
    void countsOnlyTrueBooleanAttributes() {
        Band band = Band.create("Test Band", "test-band");
        MemberAttributeDef osp = MemberAttributeDef.create(band, "OSP", "BOOLEAN", false, 0, null);
        MemberAttributeDef playing = MemberAttributeDef.create(band, "Grający", "BOOLEAN", false, 1, null);
        MemberAttributeDef textAttr = MemberAttributeDef.create(band, "Notka", "TEXT", false, 2, null);

        when(attributeDefRepository.findByBandOrderByDisplayOrderAsc(band))
                .thenReturn(List.of(osp, playing, textAttr));

        Member m = mock(Member.class);
        // OSP: 2 true, 1 false ; Grający: 0 true ; TEXT skipped
        when(attributeValueRepository.findByAttributeDef(osp)).thenReturn(List.of(
                MemberAttributeValue.create(m, osp, "true"),
                MemberAttributeValue.create(m, osp, "true"),
                MemberAttributeValue.create(m, osp, "false")));
        when(attributeValueRepository.findByAttributeDef(playing)).thenReturn(List.of(
                MemberAttributeValue.create(m, playing, "false")));
        // NOTE: textAttr is intentionally NOT stubbed — getBooleanAttributeCounts
        // skips non-BOOLEAN defs, so findByAttributeDef(textAttr) is never called.

        Map<String, Long> counts = service.getBooleanAttributeCounts(band);

        assertThat(counts).containsEntry("OSP", 2L);
        assertThat(counts).containsEntry("Grający", 0L);
        assertThat(counts).doesNotContainKey("Notka"); // TEXT excluded
    }
}
