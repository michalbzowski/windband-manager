package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the domain {@link InstrumentRoleMapRepository} port (US-1.4) on top of
 * {@link SpringDataInstrumentRoleMapRepository}. Carries the same lazy-init discipline (JOIN FETCH
 * band) as every other adapter in this module, so callers can freely traverse
 * {@code mapping.getBand().getName()} without leaking a JPA proxy or triggering an extra query.
 */
@Component
public class InstrumentRoleMapRepositoryAdapter implements InstrumentRoleMapRepository {

    private final SpringDataInstrumentRoleMapRepository springData;

    public InstrumentRoleMapRepositoryAdapter(SpringDataInstrumentRoleMapRepository springData) {
        this.springData = springData;
    }

    @Override
    public InstrumentRoleMap save(InstrumentRoleMap mapping) {
        return springData.save(mapping);
    }

    @Override
    public Optional<InstrumentRoleMap> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public List<InstrumentRoleMap> findByBandIdAndSourceTag(Long bandId, String sourceTag) {
        return springData.findByBandIdAndSourceTag(bandId, sourceTag);
    }

    @Override
    public List<InstrumentRoleMap> findAllByBandId(Long bandId) {
        return springData.findAllByBandId(bandId);
    }

    @Override
    public Optional<InstrumentRoleMap> findByBandIdAndSourceTagAndTargetRolePattern(
            Long bandId, String sourceTag, String role) {
        return springData.findByBandIdAndSourceTagAndTargetRolePattern(bandId, sourceTag, role);
    }

    @Override
    public boolean existsByBandIdAndSourceTagAndTargetRolePattern(Long bandId, String sourceTag, String role) {
        return springData.existsByBandIdAndSourceTagAndTargetRolePattern(bandId, sourceTag, role);
    }

    @Override
    public boolean existsByBandId(Long bandId) {
        return springData.existsByBandId(bandId);
    }

    @Override
    public void delete(InstrumentRoleMap mapping) {
        springData.delete(mapping);
    }

    @Override
    public int deleteAllByBandId(Long bandId) {
        return springData.deleteAllByBandId(bandId);
    }
}
