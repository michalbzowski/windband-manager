package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeDef;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeValue;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeValueRepository;
import pl.michalbzowski.windband.domain.inventory.AwardItem;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AwardAttributeValueRepositoryAdapter implements AwardAttributeValueRepository {

    private final SpringDataAwardAttributeValueRepository springDataRepo;

    @Override
    public AwardAttributeValue save(AwardAttributeValue value) {
        return springDataRepo.save(value);
    }

    @Override
    public Optional<AwardAttributeValue> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public void delete(AwardAttributeValue value) {
        springDataRepo.delete(value);
    }

    @Override
    public List<AwardAttributeValue> findByAwardItemId(Long awardItemId) {
        return springDataRepo.findByAwardItemId(awardItemId);
    }

    @Override
    public Optional<AwardAttributeValue> findByAwardItemAndAttributeDef(AwardItem item, AwardAttributeDef def) {
        return springDataRepo.findByAwardItemAndAttributeDef(item, def);
    }

    @Override
    public void deleteByAwardItemId(Long awardItemId) {
        springDataRepo.deleteByAwardItemId(awardItemId);
    }
}
