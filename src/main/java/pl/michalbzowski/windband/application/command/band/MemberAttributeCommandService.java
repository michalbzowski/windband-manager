package pl.michalbzowski.windband.application.command.band;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberAttributeCommandService {

    private final MemberAttributeDefRepository attributeDefRepository;
    private final MemberAttributeValueRepository attributeValueRepository;
    private final MemberRepository memberRepository;

    public MemberAttributeDef createAttributeDef(Band band, String name, String type, boolean required, boolean displayInList, int displayOrder, String options) {
        MemberAttributeDef def = MemberAttributeDef.create(band, name, type, required, displayInList, displayOrder, options);
        return attributeDefRepository.save(def);
    }

    public MemberAttributeDef updateAttributeDef(Long id, String name, String type, boolean required, boolean displayInList, int displayOrder, String options) {
        MemberAttributeDef def = attributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + id));
        def.update(name, type, required, displayInList, displayOrder, options);
        return attributeDefRepository.save(def);
    }

    public void deleteAttributeDef(Long id) {
        MemberAttributeDef def = attributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + id));
        attributeDefRepository.delete(def);
    }

    public MemberAttributeDef getAttributeDefById(Long id) {
        return attributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + id));
    }

    public void setAttributeValue(Long memberId, Long attributeDefId, String value) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new pl.michalbzowski.windband.application.command.member.MemberNotFoundException(memberId));
        MemberAttributeDef def = attributeDefRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + attributeDefId));

        MemberAttributeValue attrValue = attributeValueRepository.findByMemberAndAttributeDef(member, def)
                .orElse(null);

        if (attrValue == null) {
            attrValue = MemberAttributeValue.create(member, def, value);
        } else {
            attrValue.setValue(value);
        }
        attributeValueRepository.save(attrValue);
    }

    public void deleteAttributeValue(Long memberId, Long attributeDefId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new pl.michalbzowski.windband.application.command.member.MemberNotFoundException(memberId));
        MemberAttributeDef def = attributeDefRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + attributeDefId));
        attributeValueRepository.findByMemberAndAttributeDef(member, def)
                .ifPresent(attributeValueRepository::delete);
    }
}
