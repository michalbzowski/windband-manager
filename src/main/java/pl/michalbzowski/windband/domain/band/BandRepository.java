package pl.michalbzowski.windband.domain.band;

import java.util.List;
import java.util.Optional;

public interface BandRepository {

    Band save(Band band);

    Optional<Band> findById(Long id);

    Optional<Band> findByName(String name);

    List<Band> findAll();

    void delete(Band band);

    long count();
}
