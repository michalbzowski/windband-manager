package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;

import java.util.List;

public interface SpringDataMemberAttributeDefRepository extends JpaRepository<MemberAttributeDef, Long> {

    List<MemberAttributeDef> findByBand(Band band);

    List<MemberAttributeDef> findByBandOrderByDisplayOrderAsc(Band band);

    long countByBand(Band band);
}
