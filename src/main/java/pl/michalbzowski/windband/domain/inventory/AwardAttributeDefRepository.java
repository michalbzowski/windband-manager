package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface AwardAttributeDefRepository {
    AwardAttributeDef save(AwardAttributeDef def);
    Optional<AwardAttributeDef> findById(Long id);
    void delete(AwardAttributeDef def);
    List<AwardAttributeDef> findByBandAndActiveTrueOrderByDisplayOrderAsc(Band band);
}
