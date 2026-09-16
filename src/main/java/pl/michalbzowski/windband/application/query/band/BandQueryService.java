package pl.michalbzowski.windband.application.query.band;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BandQueryService {

    private final BandRepository bandRepository;

    public Band getDefaultBand() {
        return bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band (id=1) not found"));
    }

    public Band getBandById(Long id) {
        return bandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + id));
    }

    /**
     * Alias for the CQRS services — "this band must exist, a missing row is a bug".
     * Semantically identical to {@link #getBandById(Long)}; the distinct name exists so
     * acceptance criteria and code-review checklists can point at one identifier instead
     * of the repository call that used to live in each service's private helper.
     */
    public Band getRequiredBand(Long id) {
        return bandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + id));
    }
}
