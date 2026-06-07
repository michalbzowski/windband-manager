package pl.michalbzowski.windband.domain.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AwardAttributeValueRepository extends JpaRepository<AwardAttributeValue, Long> {
    List<AwardAttributeValue> findByAwardItemId(Long awardItemId);
    Optional<AwardAttributeValue> findByAwardItemAndAttributeDef(AwardItem awardItem, AwardAttributeDef attributeDef);
    void deleteByAwardItemId(Long awardItemId);
}
