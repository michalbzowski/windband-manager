package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemType;

import java.util.List;
import java.util.Optional;

public interface SpringDataItemAttributeDefRepository extends JpaRepository<ItemAttributeDef, Long> {

    List<ItemAttributeDef> findActiveByItemTypeOrderByIdAsc(ItemType itemType);

    List<ItemAttributeDef> findByBandIsNullAndActiveTrueOrderByDisplayOrderAscIdAsc();

    List<ItemAttributeDef> findByBandIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long bandId);

    Optional<ItemAttributeDef> findByIdAndActiveTrue(Long id);
}
