package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.Warehouse;
import pl.michalbzowski.windband.domain.inventory.WarehouseRepository;
import pl.michalbzowski.windband.domain.inventory.WarehouseType;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WarehouseRepositoryAdapter implements WarehouseRepository {

    private final SpringDataWarehouseRepository springDataRepo;

    @Override
    public Warehouse save(Warehouse warehouse) {
        return springDataRepo.save(warehouse);
    }

    @Override
    public Optional<Warehouse> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<Warehouse> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<Warehouse> findAllByBandId(Long bandId) {
        return springDataRepo.findByBandId(bandId);
    }

    @Override
    public List<Warehouse> findAllActiveByBandId(Long bandId) {
        return springDataRepo.findByBandIdAndActiveTrue(bandId);
    }

    @Override
    public List<Warehouse> findByTypeAndBandId(WarehouseType type, Long bandId) {
        return springDataRepo.findByTypeAndBandId(type, bandId);
    }

    @Override
    public void delete(Warehouse warehouse) {
        springDataRepo.delete(warehouse);
    }

    @Override
    public boolean existsByNameAndBandId(String name, Long bandId) {
        return springDataRepo.existsByNameAndBandId(name, bandId);
    }
}
