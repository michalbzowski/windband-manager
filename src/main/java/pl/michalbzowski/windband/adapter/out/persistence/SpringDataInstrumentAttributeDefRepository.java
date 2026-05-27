package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDef;

import java.util.List;

public interface SpringDataInstrumentAttributeDefRepository extends JpaRepository<InstrumentAttributeDef, Long> {
    List<InstrumentAttributeDef> findByBandOrderByName(Band band);
}
