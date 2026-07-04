package pl.michalbzowski.windband.domain.band;

import java.util.List;
import java.util.Optional;

public interface MemberAttributeDefRepository {

    MemberAttributeDef save(MemberAttributeDef def);

    Optional<MemberAttributeDef> findById(Long id);

    List<MemberAttributeDef> findAll();

    List<MemberAttributeDef> findByBand(Band band);

    List<MemberAttributeDef> findByBandOrderByDisplayOrderAsc(Band band);

    void delete(MemberAttributeDef def);

    long countByBand(Band band);
}
