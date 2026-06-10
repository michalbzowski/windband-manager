package pl.michalbzowski.windband.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface AwardAttributeValueRepository {
    AwardAttributeValue save(AwardAttributeValue value);
    Optional<AwardAttributeValue> findById(Long id);
    void delete(AwardAttributeValue value);
    List<AwardAttributeValue> findByAwardItemId(Long awardItemId);
    Optional<AwardAttributeValue> findByAwardItemAndAttributeDef(AwardItem item, AwardAttributeDef def);
    void deleteByAwardItemId(Long awardItemId);
}
