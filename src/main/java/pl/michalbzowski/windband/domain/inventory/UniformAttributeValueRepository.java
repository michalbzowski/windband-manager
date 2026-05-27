package pl.michalbzowski.windband.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface UniformAttributeValueRepository {
    UniformAttributeValue save(UniformAttributeValue value);
    Optional<UniformAttributeValue> findByUniformItemAndAttributeDef(UniformItem item, UniformAttributeDef def);
    List<UniformAttributeValue> findByUniformItem(UniformItem item);
    void delete(UniformAttributeValue value);
}
