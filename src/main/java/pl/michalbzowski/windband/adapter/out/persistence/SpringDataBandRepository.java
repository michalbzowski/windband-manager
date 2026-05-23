package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.Optional;

public interface SpringDataBandRepository extends JpaRepository<Band, Long> {

    Optional<Band> findByName(String name);
}
