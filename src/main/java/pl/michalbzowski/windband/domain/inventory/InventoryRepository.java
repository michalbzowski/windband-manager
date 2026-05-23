package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {

    UniformItem saveUniformItem(UniformItem item);

    List<UniformItem> findAllUniformItems();

    List<UniformItem> findUniformItemsByMember(Member member);

    Optional<UniformItem> findUniformItemById(Long id);

    void deleteUniformItem(UniformItem item);

    InstrumentItem saveInstrumentItem(InstrumentItem item);

    List<InstrumentItem> findAllInstrumentItems();

    List<InstrumentItem> findInstrumentItemsByMember(Member member);

    Optional<InstrumentItem> findInstrumentItemById(Long id);

    void deleteInstrumentItem(InstrumentItem item);
}
