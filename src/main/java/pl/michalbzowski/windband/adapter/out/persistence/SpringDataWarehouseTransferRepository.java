package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.WarehouseTransfer;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataWarehouseTransferRepository extends JpaRepository<WarehouseTransfer, Long> {

    List<WarehouseTransfer> findByItemId(Long itemId);

    List<WarehouseTransfer> findByBandId(Long bandId);

    List<WarehouseTransfer> findByFromWarehouseId(Long warehouseId);

    List<WarehouseTransfer> findByToWarehouseId(Long warehouseId);

    List<WarehouseTransfer> findByTransferDateBetween(LocalDate from, LocalDate to);

    List<WarehouseTransfer> findByBandIdAndTransferDateBetween(Long bandId, LocalDate from, LocalDate to);
}
