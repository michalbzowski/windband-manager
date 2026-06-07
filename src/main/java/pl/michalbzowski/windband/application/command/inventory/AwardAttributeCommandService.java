package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AwardAttributeCommandService {

    private final AwardAttributeDefRepository defRepository;
    private final AwardAttributeValueRepository valueRepository;
    private final AwardItemRepository awardItemRepository;

    public AwardAttributeDef createAttributeDef(Band band, String name, String type, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
        AwardAttributeDef def = AwardAttributeDef.create(band, name, type, required, displayInList, displayOrder, options);
        def.setDependsOnAttributeId(dependsOnAttributeId);
        def.setDependsOnValue(dependsOnValue);
        return defRepository.save(def);
    }

    public AwardAttributeDef updateAttributeDef(Long id, String name, String type, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
        AwardAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AwardAttributeDef not found: " + id));
        def.update(name, type, required, displayInList, displayOrder, options);
        def.setDependsOnAttributeId(dependsOnAttributeId);
        def.setDependsOnValue(dependsOnValue);
        return defRepository.save(def);
    }

    public void deleteAttributeDef(Long id) {
        AwardAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AwardAttributeDef not found: " + id));
        defRepository.delete(def);
    }

    public AwardAttributeDef getAttributeDefById(Long id) {
        return defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AwardAttributeDef not found: " + id));
    }

    public void setAttributeValue(Long awardItemId, Long attributeDefId, String value) {
        AwardItem item = awardItemRepository.findById(awardItemId)
                .orElseThrow(() -> new IllegalArgumentException("AwardItem not found: " + awardItemId));
        AwardAttributeDef def = defRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("AwardAttributeDef not found: " + attributeDefId));
        setAttributeValue(item, def, value);
    }

    public void setAttributeValue(AwardItem item, AwardAttributeDef def, String value) {
        AwardAttributeValue attrValue = valueRepository.findByAwardItemAndAttributeDef(item, def)
                .orElse(null);
        if (attrValue == null) {
            attrValue = AwardAttributeValue.create(item, def, value);
        } else {
            attrValue.setValue(value);
        }
        valueRepository.save(attrValue);
    }
}
