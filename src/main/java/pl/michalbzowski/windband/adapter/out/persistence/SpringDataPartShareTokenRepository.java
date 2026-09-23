package pl.michalbzowski.windband.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.composition.PartShareToken;

/**
 * Spring Data adapter for {@link PartShareToken} (US-7.11).
 *
 * <p>Token lookup fetches {@code JOIN FETCH part} — the public link path immediately
 * walks {@code token.getPart().getComposition()} and must not lazy-load outside the
 * read transaction.</p>
 */
public interface SpringDataPartShareTokenRepository extends JpaRepository<PartShareToken, Long> {

    @Query("""
            SELECT t FROM PartShareToken t
            JOIN FETCH t.part p
            JOIN FETCH p.composition
            WHERE t.token = :token""")
    Optional<PartShareToken> findByToken(@Param("token") UUID token);

    @Query("""
            SELECT t FROM PartShareToken t
            JOIN FETCH t.part p
            JOIN FETCH p.composition
            WHERE t.part.id = :partId""")
    Optional<PartShareToken> findByPartId(@Param("partId") Long partId);

    List<PartShareToken> findAllByPartId(Long partId);
}
