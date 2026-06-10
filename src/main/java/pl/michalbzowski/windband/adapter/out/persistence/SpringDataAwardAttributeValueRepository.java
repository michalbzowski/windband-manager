package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeDef;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeValue;
import pl.michalbzowski.windband.domain.inventory.AwardItem;

import java.util.List;
import java.util.Optional;

public interface SpringDataAwardAttributeValueRepository extends JpaRepository<AwardAttributeValue, Long> {
    List<AwardAttributeValue> findByAwardItemId(Long awardItemId);
    Optional<AwardAttributeValue> findByAwardItemAndAttributeDef(AwardItem item, AwardAttributeDef def);
    void deleteByAwardItemId(Long awardItemId);
}
