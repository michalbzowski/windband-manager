package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.WarehouseTransfer;
import pl.michalbzowski.windband.domain.inventory.WarehouseTransferRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WarehouseTransferRepositoryAdapter implements WarehouseTransferRepository {

    private final SpringDataWarehouseTransferRepository springDataRepo;

    @Override
    public WarehouseTransfer save(WarehouseTransfer transfer) {
        return springDataRepo.save(transfer);
    }

    @Override
    public Optional<WarehouseTransfer> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<WarehouseTransfer> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<WarehouseTransfer> findByItemId(Long itemId) {
        return springDataRepo.findByItemId(itemId);
    }

    @Override
    public List<WarehouseTransfer> findByBandId(Long bandId) {
        return springDataRepo.findByBandId(bandId);
    }

    @Override
    public List<WarehouseTransfer> findByFromWarehouseId(Long warehouseId) {
        return springDataRepo.findByFromWarehouseId(warehouseId);
    }

    @Override
    public List<WarehouseTransfer> findByToWarehouseId(Long warehouseId) {
        return springDataRepo.findByToWarehouseId(warehouseId);
    }

    @Override
    public List<WarehouseTransfer> findByDateRange(LocalDate from, LocalDate to) {
        return springDataRepo.findByTransferDateBetween(from, to);
    }

    @Override
    public List<WarehouseTransfer> findByBandIdAndDateRange(Long bandId, LocalDate from, LocalDate to) {
        return springDataRepo.findByBandIdAndTransferDateBetween(bandId, from, to);
    }
}
