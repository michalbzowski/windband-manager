package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDef;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDefRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderAttributeDefRepositoryAdapter implements OrderAttributeDefRepository {

    private final SpringDataOrderAttributeDefRepository springDataRepo;

    @Override
    public List<OrderAttributeDef> findByBand(Band band) {
        return springDataRepo.findByBandOrderByDisplayOrder(band);
    }

    @Override
    public Optional<OrderAttributeDef> findByBandAndName(Band band, String name) {
        return springDataRepo.findByBandAndName(band, name);
    }

    @Override
    public OrderAttributeDef save(OrderAttributeDef def) {
        return springDataRepo.save(def);
    }

    @Override
    public Optional<OrderAttributeDef> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public void delete(OrderAttributeDef def) {
        springDataRepo.delete(def);
    }
}
