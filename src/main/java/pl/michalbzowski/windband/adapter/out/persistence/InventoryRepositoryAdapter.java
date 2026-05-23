package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.InstrumentItem;
import pl.michalbzowski.windband.domain.inventory.InventoryRepository;
import pl.michalbzowski.windband.domain.inventory.UniformItem;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryRepositoryAdapter implements InventoryRepository {

    private final SpringDataUniformItemRepository uniformRepo;
    private final SpringDataInstrumentItemRepository instrumentRepo;

    @Override
    public UniformItem saveUniformItem(UniformItem item) {
        return uniformRepo.save(item);
    }

    @Override
    public List<UniformItem> findAllUniformItems() {
        return uniformRepo.findAll();
    }

    @Override
    public List<UniformItem> findUniformItemsByMember(Member member) {
        return uniformRepo.findByAssignedMember(member);
    }

    @Override
    public Optional<UniformItem> findUniformItemById(Long id) {
        return uniformRepo.findById(id);
    }

    @Override
    public void deleteUniformItem(UniformItem item) {
        uniformRepo.delete(item);
    }

    @Override
    public InstrumentItem saveInstrumentItem(InstrumentItem item) {
        return instrumentRepo.save(item);
    }

    @Override
    public List<InstrumentItem> findAllInstrumentItems() {
        return instrumentRepo.findAll();
    }

    @Override
    public List<InstrumentItem> findInstrumentItemsByMember(Member member) {
        return instrumentRepo.findByAssignedMember(member);
    }

    @Override
    public Optional<InstrumentItem> findInstrumentItemById(Long id) {
        return instrumentRepo.findById(id);
    }

    @Override
    public void deleteInstrumentItem(InstrumentItem item) {
        instrumentRepo.delete(item);
    }
}
