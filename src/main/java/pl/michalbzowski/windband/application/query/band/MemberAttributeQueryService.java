package pl.michalbzowski.windband.application.query.band;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAttributeQueryService {

    private final MemberAttributeDefRepository attributeDefRepository;
    private final MemberAttributeValueRepository attributeValueRepository;

    public List<MemberAttributeDef> getAttributeDefsForBand(Band band) {
        return attributeDefRepository.findByBandOrderByDisplayOrderAsc(band);
    }

    public Map<Long, String> getAttributeValuesForMember(Member member) {
        Map<Long, String> result = new LinkedHashMap<>();
        List<MemberAttributeValue> values = attributeValueRepository.findByMember(member);
        for (MemberAttributeValue v : values) {
            result.put(v.getAttributeDef().getId(), v.getValue());
        }
        return result;
    }

    public Optional<String> getAttributeValue(Member member, Long attributeDefId) {
        MemberAttributeDef def = attributeDefRepository.findById(attributeDefId).orElse(null);
        if (def == null) return Optional.empty();
        return attributeValueRepository.findByMemberAndAttributeDef(member, def)
                .map(MemberAttributeValue::getValue);
    }
}
