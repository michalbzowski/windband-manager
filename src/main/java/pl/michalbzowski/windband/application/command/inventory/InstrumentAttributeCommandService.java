package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.*;

@Service
@RequiredArgsConstructor
@Transactional
public class InstrumentAttributeCommandService {

    private final InstrumentAttributeDefRepository defRepository;
    private final InstrumentAttributeValueRepository valueRepository;
    private final InventoryRepository inventoryRepository;

    public InstrumentAttributeDef createAttributeDef(Band band, String name, String type, boolean required, int displayOrder, String options) {
        InstrumentAttributeDef def = InstrumentAttributeDef.create(band, name, type, required, displayOrder, options);
        return defRepository.save(def);
    }

    public InstrumentAttributeDef updateAttributeDef(Long id, String name, String type, boolean required, int displayOrder, String options) {
        InstrumentAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("InstrumentAttributeDef not found: " + id));
        def.update(name, type, required, displayOrder, options);
        return defRepository.save(def);
    }

    public void deleteAttributeDef(Long id) {
        InstrumentAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("InstrumentAttributeDef not found: " + id));
        defRepository.delete(def);
    }

    public InstrumentAttributeDef getAttributeDefById(Long id) {
        return defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("InstrumentAttributeDef not found: " + id));
    }

    public void setAttributeValue(Long instrumentItemId, Long attributeDefId, String value) {
        InstrumentItem item = inventoryRepository.findInstrumentItemById(instrumentItemId)
                .orElseThrow(() -> new InventoryItemNotFoundException(instrumentItemId));
        InstrumentAttributeDef def = defRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("InstrumentAttributeDef not found: " + attributeDefId));
        setAttributeValue(item, def, value);
    }

    public void setAttributeValue(InstrumentItem item, InstrumentAttributeDef def, String value) {
        InstrumentAttributeValue attrValue = valueRepository.findByInstrumentItemAndAttributeDef(item, def)
                .orElse(null);
        if (attrValue == null) {
            attrValue = InstrumentAttributeValue.create(item, def, value);
        } else {
            attrValue.setValue(value);
        }
        valueRepository.save(attrValue);
    }
}
