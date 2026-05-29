package pl.michalbzowski.windband.domain.band;

import java.util.List;
import java.util.Optional;

public interface BandRepository {

    Band save(Band band);

    Band saveAndFlush(Band band);

    Optional<Band> findById(Long id);

    Optional<Band> findByName(String name);

    Optional<Band> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Band> findAll();

    void delete(Band band);

    long count();
}
