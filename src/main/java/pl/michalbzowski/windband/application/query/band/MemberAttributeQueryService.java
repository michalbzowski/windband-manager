package pl.michalbzowski.windband.application.query.band;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.MemberAttributeDefDto;
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

    public List<MemberAttributeDefDto> getAttributeDefsForBand(Band band) {
        return attributeDefRepository.findByBandOrderByDisplayOrderAsc(band).stream()
                .map(this::toDto)
                .toList();
    }

    private MemberAttributeDefDto toDto(MemberAttributeDef def) {
        return new MemberAttributeDefDto(
                def.getId(),
                def.getName(),
                def.getType(),
                def.isRequired(),
                def.isDisplayInList(),
                def.getDisplayOrder(),
                def.getOptions()
        );
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

    /**
     * Returns a map of BOOLEAN attribute name -> number of members in the band
     * whose value for that attribute equals "true". Only BOOLEAN attribute defs
     * are included. Order follows displayOrder.
     */
    public Map<String, Long> getBooleanAttributeCounts(Band band) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<MemberAttributeDef> defs = attributeDefRepository.findByBandOrderByDisplayOrderAsc(band);
        for (MemberAttributeDef def : defs) {
            if (!"BOOLEAN".equals(def.getType())) {
                continue;
            }
            long count = attributeValueRepository.findByAttributeDef(def).stream()
                    .filter(v -> "true".equals(v.getValue()))
                    .count();
            result.put(def.getName(), count);
        }
        return result;
    }
}
