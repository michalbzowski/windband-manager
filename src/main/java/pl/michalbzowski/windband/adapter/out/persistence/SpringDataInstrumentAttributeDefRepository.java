package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDef;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDefRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataInstrumentAttributeDefRepository extends JpaRepository<InstrumentAttributeDef, Long>, InstrumentAttributeDefRepository {
    List<InstrumentAttributeDef> findByBandOrderByName(Band band);

    @Override
    Optional<InstrumentAttributeDef> findByBandAndName(Band band, String name);
}
