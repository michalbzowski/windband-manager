package pl.michalbzowski.windband.domain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PrivatePossessionDeclarationRepository {

    PrivatePossessionDeclaration save(PrivatePossessionDeclaration declaration);

    Optional<PrivatePossessionDeclaration> findById(Long id);

    List<PrivatePossessionDeclaration> findAll();

    List<PrivatePossessionDeclaration> findByMemberId(Long memberId);

    List<PrivatePossessionDeclaration> findByBandId(Long bandId);

    List<PrivatePossessionDeclaration> findByBandIdAndStatus(Long bandId, DeclarationStatus status);

    List<PrivatePossessionDeclaration> findByItemTypeAndBandId(ItemType itemType, Long bandId);

    List<PrivatePossessionDeclaration> findActiveByBandId(Long bandId);

    List<PrivatePossessionDeclaration> findExpiringSoon(Long bandId, LocalDate beforeDate);

    void delete(PrivatePossessionDeclaration declaration);
}