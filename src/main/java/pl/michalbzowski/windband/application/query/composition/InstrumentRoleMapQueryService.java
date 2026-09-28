package pl.michalbzowski.windband.application.query.composition;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.composition.InstrumentRoleMapDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;

import java.util.List;

/**
 * Read side of the {@code instrument_role_map} admin surface (US-7.3): lists a band's
 * tag→role mappings as DTO projections so the Thymeleaf page never touches the lazy
 * {@code Band} association.
 *
 * <p>Band validation is fail-closed: an unknown {@code bandId} throws
 * {@link IllegalArgumentException} (→ HTTP 400) before any repository read, mirroring the
 * {@code getRequiredBand} discipline used by every other query service in this module.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentRoleMapQueryService {

    private final BandQueryService bandQueryService;
    private final InstrumentRoleMapRepository instrumentRoleMapRepository;

    /**
     * All tag→role mappings owned by {@code bandId}, already sorted by (sourceTag, role)
     * via the adapter's {@code JOIN FETCH} query — safe to render after the session closes.
     */
    public List<InstrumentRoleMapDto> listByBand(Long bandId) {
        bandQueryService.getRequiredBand(bandId); // unknown band → IAE (400); no cross-band read possible below
        return instrumentRoleMapRepository.findAllByBandId(bandId).stream()
                .map(mapping -> new InstrumentRoleMapDto(
                        mapping.getId(),
                        mapping.getSourceTag(),
                        mapping.getTargetRolePattern(),
                        mapping.getDescription()))
                .toList();
    }
}
