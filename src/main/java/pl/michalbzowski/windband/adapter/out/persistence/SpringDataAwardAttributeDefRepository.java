package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeDef;

import java.util.List;

public interface SpringDataAwardAttributeDefRepository extends JpaRepository<AwardAttributeDef, Long> {
    List<AwardAttributeDef> findByBandAndActiveTrueOrderByDisplayOrderAsc(Band band);
}
