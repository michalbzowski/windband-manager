package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDef;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeValue;
import pl.michalbzowski.windband.domain.inventory.InstrumentItem;

import java.util.List;
import java.util.Optional;

public interface SpringDataInstrumentAttributeValueRepository extends JpaRepository<InstrumentAttributeValue, Long> {
    Optional<InstrumentAttributeValue> findByInstrumentItemAndAttributeDef(InstrumentItem item, InstrumentAttributeDef def);
    List<InstrumentAttributeValue> findByInstrumentItem(InstrumentItem item);
}
