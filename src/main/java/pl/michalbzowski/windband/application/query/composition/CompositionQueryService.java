package pl.michalbzowski.windband.application.query.composition;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.composition.CompositionDto;
import pl.michalbzowski.windband.application.dto.composition.CompositionInstrumentDto;
import pl.michalbzowski.windband.application.dto.composition.CompositionWithPartsDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

import java.util.List;

/**
 * Query side of the score-library module: read-only listing, retrieval and
 * search of {@link Composition} rows. Complements
 * {@code CompositionCommandService} (CQRS split per project convention).
 *
 * <p><b>Band isolation:</b> every read goes through the domain repository port's
 * band-scoped methods ({@code findAllByBand}, {@code findByIdAndBandId},
 * {@code search(bandId, term)}) — a caller from band B can never load, list
 * or find a row owned by band A: the pair simply does not resolve. Band
 * existence itself is enforced upstream via
 * {@link BandQueryService#getRequiredBand(Long)} (US-1.6 AC "band isolation
 * enforced via BandQueryService.getRequiredBand").
 *
 * <p><b>Lazy-init safety:</b> list/search paths return rows whose lazy
 * {@code band} association the Spring Data adapter resolves with an explicit
 * {@code JOIN FETCH} (see {@code SpringDataCompositionRepository}). The
 * {@link CompositionInstrument} parts association is deliberately NOT fetched
 * on those read paths — a row's parts are only resolved by
 * {@link #getCompositionWithParts(Long, Long)}, which projects them into
 * DTOs while the transaction is still open so Thymeleaf or an HTTP response
 * never touches a detached lazy proxy (Shape-C pattern).
 *
 * <p><b>Error semantics</b> — single source of truth, consistent with the
 * command side (see {@code GlobalExceptionHandler} mapping):
 * <ul>
 *   <li>unknown (i.e. non-existent) band id → {@link IllegalArgumentException} (HTTP 400)</li>
 *   <li>composition id not found in the calling band, or cross-band access attempt
 *       → {@link IllegalStateException} (HTTP 409)</li>
 * </ul>
 *
 * <p><b>ArchUnit gate:</b> this class must not depend on Spring Web or the adapter
 * layer. It injects only domain ports — keep it that way.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompositionQueryService {

    private final CompositionRepository repository;
    private final BandQueryService bandQueryService;
    private final CompositionInstrumentRepository instrumentRepository;

    /**
     * All compositions of the given band, most-recently-updated first
     * (ordering is guaranteed by the repository adapter's query contract).
     *
     * @param bandId        the calling band — required to exist
     * @param statusFilter  optional filter; {@code null} means "all statuses"
     */
    public List<Composition> listByBand(Long bandId, CompositionStatus statusFilter) {
        Band band = bandQueryService.getRequiredBand(bandId);
        if (statusFilter == null) {
            return repository.findAllByBand(band);
        }
        return repository.findAllByBand(band).stream()
                .filter(c -> c.getStatus() == statusFilter)
                .toList();
    }

    /**
     * Paginated version of {@link #listByBand(Long, CompositionStatus)}.
     */
    public Page<Composition> listByBand(Long bandId, CompositionStatus statusFilter, Pageable pageable) {
        Band band = bandQueryService.getRequiredBand(bandId);
        if (statusFilter == null) {
            return repository.findAllByBand(band, pageable);
        }
        return repository.findAllByBandAndStatus(band, statusFilter, pageable);
    }

    /**
     * A single composition, resolvable only inside the calling band. A non-existent
     * or foreign-band id fails closed with {@link IllegalStateException} — see the
     * class-level error semantics for the HTTP mapping of that outcome.
     */
    public Composition get(Long id, Long bandId) {
        bandQueryService.getRequiredBand(bandId);
        return repository.findByIdAndBandId(id, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + id + " does not belong to band " + bandId));
    }

    /**
     * Case-insensitive search across title, composer and arranger within the
     * calling band. Reuses the repository port's band-scoped contract directly.
     *
     * @return matches in descending {@code updatedAt} order; never {@code null},
     *         empty when nothing matches (or when the band owns no rows).
     */
    public List<Composition> search(Long bandId, String term) {
        bandQueryService.getRequiredBand(bandId);
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return repository.search(bandId, term);
    }

    /**
     * Full US-3.03 read target: the composition <b>and</b> every part-row of it,
     * projected into immutable DTOs <i>inside this open read-only transaction</i>.
     * Callers never see a lazy {@code instrument} association — resolving it here
     * is what prevents the classic post-commit
     * {@code LazyInitializationException} when Thymeleaf (or an HTTP response
     * renderer) walks the parts list later.
     *
     * @return non-null; {@code getCompositionWithParts} fails closed (409 shape)
     *         for unknown or foreign-band ids, exactly like {@link #get} does.
     */
    public CompositionWithPartsDto getCompositionWithParts(Long id, Long bandId) {
        bandQueryService.getRequiredBand(bandId);
        Composition composition = repository.findByIdAndBandId(id, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + id + " does not belong to band " + bandId));
        List<CompositionInstrument> parts = instrumentRepository.findAllByComposition(composition);
        List<CompositionInstrumentDto> partDtos = parts.stream()
                .map(p -> toPartDto(p, composition.getBand()))
                .toList();
        return new CompositionWithPartsDto(CompositionDto.from(composition), partDtos);
    }

    private CompositionInstrumentDto toPartDto(CompositionInstrument p, Band band) {
        String instrumentName = p.getInstrument() != null ? p.getInstrument().getName() : null;
        return new CompositionInstrumentDto(
                p.getId(),
                p.getComposition() != null ? p.getComposition().getId() : null,
                p.getInstrumentRole(),
                instrumentName,
                p.getPageFrom(),
                p.getPageTo(),
                p.getFileRef(),
                p.getSource(),
                p.getConfidenceScore(),
                p.getVerifiedBy(),
                p.getVerifiedAt());
    }

}
