package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.*;

@Service
@RequiredArgsConstructor
@Transactional
public class UniformAttributeCommandService {

    private final UniformAttributeDefRepository defRepository;
    private final UniformAttributeValueRepository valueRepository;
    private final InventoryRepository inventoryRepository;

    public UniformAttributeDef createAttributeDef(Band band, String name, String type, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
        UniformAttributeDef def = UniformAttributeDef.create(band, name, type, required, displayInList, displayOrder, options);
        def.setDependsOnAttributeId(dependsOnAttributeId);
        def.setDependsOnValue(dependsOnValue);
        return defRepository.save(def);
    }

    public UniformAttributeDef updateAttributeDef(Long id, String name, String type, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
        UniformAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UniformAttributeDef not found: " + id));
        def.update(name, type, required, displayInList, displayOrder, options);
        def.setDependsOnAttributeId(dependsOnAttributeId);
        def.setDependsOnValue(dependsOnValue);
        return defRepository.save(def);
    }

    public void deleteAttributeDef(Long id) {
        UniformAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UniformAttributeDef not found: " + id));
        defRepository.delete(def);
    }

    public UniformAttributeDef getAttributeDefById(Long id) {
        return defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UniformAttributeDef not found: " + id));
    }

    public void setAttributeValue(Long uniformItemId, Long attributeDefId, String value) {
        UniformItem item = inventoryRepository.findUniformItemById(uniformItemId)
                .orElseThrow(() -> new InventoryItemNotFoundException(uniformItemId));
        UniformAttributeDef def = defRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("UniformAttributeDef not found: " + attributeDefId));
        setAttributeValue(item, def, value);
    }

    public void setAttributeValue(UniformItem item, UniformAttributeDef def, String value) {
        UniformAttributeValue attrValue = valueRepository.findByUniformItemAndAttributeDef(item, def)
                .orElse(null);
        if (attrValue == null) {
            attrValue = UniformAttributeValue.create(item, def, value);
        } else {
            attrValue.setValue(value);
        }
        valueRepository.save(attrValue);
    }
}
