package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final MemberRepository memberRepository;
    private final BandRepository bandRepository;

    public UniformItem addUniformItem(String name, String description, Long memberId, OwnershipStatus status) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
        UniformItem item = switch (status) {
            case OWNED -> UniformItem.createOwned(name, band);
            case BORROWED -> UniformItem.createBorrowed(name, band);
            case MISSING -> UniformItem.createMissing(name, band);
        };

        if (description != null) {
            // UniformItem doesn't have updateDescription, but we can extend it later
        }

        if (memberId != null) {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new MemberNotFoundException(memberId));
            item.assignTo(member);
        }

        return inventoryRepository.saveUniformItem(item);
    }

    public InstrumentItem addInstrumentItem(String name, String brand, String serialNumber,
                                             String description, Long memberId, OwnershipStatus status) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
        InstrumentItem item = switch (status) {
            case OWNED -> InstrumentItem.createOwned(name, band);
            case BORROWED -> InstrumentItem.createBorrowed(name, band);
            case MISSING -> InstrumentItem.createMissing(name, band);
        };

        if (brand != null || serialNumber != null || description != null) {
            item.updateDetails(brand, serialNumber, description);
        }

        if (memberId != null) {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new MemberNotFoundException(memberId));
            item.assignTo(member);
        }

        return inventoryRepository.saveInstrumentItem(item);
    }

    public void updateUniformOwnership(Long itemId, OwnershipStatus status) {
        UniformItem item = inventoryRepository.findUniformItemById(itemId)
                .orElseThrow(() -> new InventoryItemNotFoundException(itemId));
        item.updateOwnershipStatus(status);
        inventoryRepository.saveUniformItem(item);
    }

    public void updateInstrumentOwnership(Long itemId, OwnershipStatus status) {
        InstrumentItem item = inventoryRepository.findInstrumentItemById(itemId)
                .orElseThrow(() -> new InventoryItemNotFoundException(itemId));
        item.updateOwnershipStatus(status);
        inventoryRepository.saveInstrumentItem(item);
    }

    public void deleteUniformItem(Long itemId) {
        UniformItem item = inventoryRepository.findUniformItemById(itemId)
                .orElseThrow(() -> new InventoryItemNotFoundException(itemId));
        inventoryRepository.deleteUniformItem(item);
    }

    public void deleteInstrumentItem(Long itemId) {
        InstrumentItem item = inventoryRepository.findInstrumentItemById(itemId)
                .orElseThrow(() -> new InventoryItemNotFoundException(itemId));
        inventoryRepository.deleteInstrumentItem(item);
    }
}
