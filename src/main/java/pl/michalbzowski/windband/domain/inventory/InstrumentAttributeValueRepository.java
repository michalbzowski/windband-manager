package pl.michalbzowski.windband.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface InstrumentAttributeValueRepository {
    InstrumentAttributeValue save(InstrumentAttributeValue value);
    Optional<InstrumentAttributeValue> findByInstrumentItemAndAttributeDef(InstrumentItem item, InstrumentAttributeDef def);
    List<InstrumentAttributeValue> findByInstrumentItem(InstrumentItem item);
    void delete(InstrumentAttributeValue value);
}
