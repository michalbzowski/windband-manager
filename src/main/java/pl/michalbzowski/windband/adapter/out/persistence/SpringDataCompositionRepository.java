package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataCompositionRepository extends JpaRepository<Composition, Long> {

    Optional<Composition> findByIdAndBandId(Long id, Long bandId);

    List<Composition> findAllByBandOrderByUpdatedAtDesc(Band band);

    boolean existsByIdAndBandId(Long id, Long bandId);
}
