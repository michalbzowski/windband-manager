package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDef;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeValue;
import pl.michalbzowski.windband.domain.inventory.UniformItem;

import java.util.List;
import java.util.Optional;

public interface SpringDataUniformAttributeValueRepository extends JpaRepository<UniformAttributeValue, Long> {
    Optional<UniformAttributeValue> findByUniformItemAndAttributeDef(UniformItem item, UniformAttributeDef def);
    List<UniformAttributeValue> findByUniformItem(UniformItem item);
}
