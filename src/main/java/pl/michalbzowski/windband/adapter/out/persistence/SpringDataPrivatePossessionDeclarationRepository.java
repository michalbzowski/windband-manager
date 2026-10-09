package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.inventory.DeclarationStatus;
import pl.michalbzowski.windband.domain.inventory.ItemType;
import pl.michalbzowski.windband.domain.inventory.PrivatePossessionDeclaration;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataPrivatePossessionDeclarationRepository extends JpaRepository<PrivatePossessionDeclaration, Long> {

    List<PrivatePossessionDeclaration> findByMemberId(Long memberId);

    List<PrivatePossessionDeclaration> findByBandId(Long bandId);

    List<PrivatePossessionDeclaration> findByBandIdAndStatus(Long bandId, DeclarationStatus status);

    List<PrivatePossessionDeclaration> findByItemTypeAndBandId(ItemType itemType, Long bandId);

    @Query("SELECT d FROM PrivatePossessionDeclaration d WHERE d.band.id = :bandId AND d.status = 'ACTIVE' AND (d.validUntil IS NULL OR d.validUntil >= CURRENT_DATE)")
    List<PrivatePossessionDeclaration> findActiveByBandId(@Param("bandId") Long bandId);

    @Query("SELECT d FROM PrivatePossessionDeclaration d WHERE d.band.id = :bandId AND d.status = 'ACTIVE' AND d.validUntil IS NOT NULL AND d.validUntil <= :beforeDate")
    List<PrivatePossessionDeclaration> findExpiringSoon(@Param("bandId") Long bandId, @Param("beforeDate") LocalDate beforeDate);
}
