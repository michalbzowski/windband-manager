package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.Warehouse;
import pl.michalbzowski.windband.domain.inventory.WarehouseType;

import java.util.List;

public interface SpringDataWarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findByBandId(Long bandId);

    List<Warehouse> findByBandIdAndActiveTrue(Long bandId);

    List<Warehouse> findByTypeAndBandId(WarehouseType type, Long bandId);

    boolean existsByNameAndBandId(String name, Long bandId);
}
