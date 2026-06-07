package pl.michalbzowski.windband.domain.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;

@Repository
public interface AwardAttributeDefRepository extends JpaRepository<AwardAttributeDef, Long> {
    List<AwardAttributeDef> findByBandAndActiveTrueOrderByDisplayOrderAsc(Band band);
}
