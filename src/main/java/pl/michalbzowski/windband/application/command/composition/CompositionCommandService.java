package pl.michalbzowski.windband.application.command.composition;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;

/**
 * Command side of the score-library module: create, update-texts, archive
 * and restore a {@link Composition}.
 *
 * <p><b>Band isolation:</b> every read of an existing row goes through
 * {@link CompositionRepository#findByIdAndBandId(Long, Long)}, so a caller from band B can
 * never even load a row owned by band A: the pair simply does not resolve.
 *
 * <p><b>Error semantics</b> (single source of truth — see global handler mapping):
 * <ul>
 *   <li>blank title, or no band for the id → {@link IllegalArgumentException} (HTTP 400)</li>
 *   <li>title over 200 chars (re-checked in the domain on update) → {@code IllegalArgumentException} (HTTP 400)</li>
 *   <li>composition id not found in band, or cross-band access attempt → {@link IllegalStateException} (HTTP 409)</li>
 * </ul>
 *
 * <p><b>ArchUnit gate:</b> this class must not depend on Spring Web or the adapter
 * layer. It injects only domain ports — keep it that way.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompositionCommandService {

    private final CompositionRepository repository;
    private final BandQueryService bandQueryService;

    // ---- create ----------------------------------------------------------

    public Composition create(CreateCompositionCommand cmd, Long bandId) {
        Band band = bandQueryService.getRequiredBand(bandId);
        if (cmd.getTitle() == null || cmd.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Tytuł jest wymagany");
        }
        // Over-long titles are caught by the domain factory (Composition.create → requireTitle),
        // so no duplicate check is needed here.
        return repository.save(Composition.create(
                cmd.getTitle(),
                cmd.getDescription(),
                cmd.getComposer(),
                cmd.getArranger(),
                band));
    }

    // ---- update ----------------------------------------------------------

    public Composition update(Long id, UpdateCompositionCommand cmd, Long bandId) {
        Composition composition = requireOwned(id, bandId);
        composition.updateTexts(cmd.getTitle(), cmd.getDescription(),
                                cmd.getComposer(), cmd.getArranger());
        return repository.save(composition);
    }

    // ---- archive / restore -----------------------------------------------

    public void archive(Long id, Long bandId) {
        requireOwned(id, bandId).archive();
    }

    /** Revert an archived composition back to DRAFT (US lifecycle step). */
    public void restore(Long id, Long bandId) {
        requireOwned(id, bandId).restore();
    }

    // ---- delete (US-1.6 AC) ----------------------------------------------

    /**
     * Physically deletes the composition and — via V35/V38 FK {@code ON DELETE CASCADE}
     * — every dependent {@code composition_instruments} / {@code score_files} row
     * in the same statement set. A foreign-band id fails closed (409), never
     * touching another band's row.
     */
    public void deleteComposition(Long id, Long bandId) {
        Composition owned = requireOwned(id, bandId);
        // Cascade net: parts rows are orphan-removed by JPA too; both paths apply.
        repository.delete(owned);
    }

    // ---- failure helpers --------------------------------------------------

    /**
     * A composition id that does not resolve in the calling band is always a
     * multi-tenant issue (either another band owns it, or it simply does not
     * exist). Both cases are treated as conflicts at this layer → 409 via the
     * {@link IllegalStateException} mapping in
     * {@code GlobalExceptionHandler#handleConflict}.
     */
    private Composition requireOwned(Long id, Long bandId) {
        return repository.findByIdAndBandId(id, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + id + " does not belong to band " + bandId));
    }
}
