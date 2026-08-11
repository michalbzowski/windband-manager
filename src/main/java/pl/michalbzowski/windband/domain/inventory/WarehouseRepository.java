package pl.michalbzowski.windband.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository {

    Warehouse save(Warehouse warehouse);

    Optional<Warehouse> findById(Long id);

    List<Warehouse> findAll();

    List<Warehouse> findAllByBandId(Long bandId);

    List<Warehouse> findAllActiveByBandId(Long bandId);

    List<Warehouse> findByTypeAndBandId(WarehouseType type, Long bandId);

    void delete(Warehouse warehouse);

    boolean existsByNameAndBandId(String name, Long bandId);
}