package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDef;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeValue;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeValueRepository;
import pl.michalbzowski.windband.domain.inventory.UniformItem;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UniformAttributeValueRepositoryAdapter implements UniformAttributeValueRepository {

    private final SpringDataUniformAttributeValueRepository springDataRepo;

    @Override
    public UniformAttributeValue save(UniformAttributeValue value) {
        return springDataRepo.save(value);
    }

    @Override
    public Optional<UniformAttributeValue> findByUniformItemAndAttributeDef(UniformItem item, UniformAttributeDef def) {
        return springDataRepo.findByUniformItemAndAttributeDef(item, def);
    }

    @Override
    public List<UniformAttributeValue> findByUniformItem(UniformItem item) {
        return springDataRepo.findByUniformItem(item);
    }

    @Override
    public void delete(UniformAttributeValue value) {
        springDataRepo.delete(value);
    }
}
