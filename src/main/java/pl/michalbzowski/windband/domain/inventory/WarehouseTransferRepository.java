package pl.michalbzowski.windband.domain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WarehouseTransferRepository {

    WarehouseTransfer save(WarehouseTransfer transfer);

    Optional<WarehouseTransfer> findById(Long id);

    List<WarehouseTransfer> findAll();

    List<WarehouseTransfer> findByItemId(Long itemId);

    List<WarehouseTransfer> findByBandId(Long bandId);

    List<WarehouseTransfer> findByFromWarehouseId(Long warehouseId);

    List<WarehouseTransfer> findByToWarehouseId(Long warehouseId);

    List<WarehouseTransfer> findByDateRange(LocalDate from, LocalDate to);

    List<WarehouseTransfer> findByBandIdAndDateRange(Long bandId, LocalDate from, LocalDate to);
}