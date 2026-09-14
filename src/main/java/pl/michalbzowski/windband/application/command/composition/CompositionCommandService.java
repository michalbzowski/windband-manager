package pl.michalbzowski.windband.application.command.composition;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;

/**
 * Command side of the score-library module (C — Commands): create, update-texts,
 * archive and restore a {@link Composition}.
 *
 * <p><b>Band isolation</b> — every read of an existing row goes through
 * {@link CompositionRepository#findByIdAndBandId(Long, Long)}, so a caller from band B can
 * never even load a row owned by band A: the pair simply does not resolve. The service maps
 * that miss to {@link IllegalStateException} (→ HTTP 409 Conflict via the global handler),
 * which is this project's established idiom for multi-tenant access conflicts; input errors
 * (blank/over-long title, unknown band) map to {@link IllegalArgumentException} (→ 400).
 * See US-multi-tenant contract and the existing band-isolation services (rehearsal, member).
 *
 * <p><b>ArchUnit gate:</b> this class must not depend on Spring Web or the adapter layer.
 * It injects only domain ports — keep it that way.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompositionCommandService {

    static final int MAX_TITLE_LENGTH = 200;

    private final CompositionRepository repository;
    private final BandRepository bandRepository;

    // ---- create ----------------------------------------------------------

    /**
     * Persist a brand-new draft composition for the given band.
     *
     * @throws IllegalArgumentException if the title is blank or exceeds
     *                                  {@value #MAX_TITLE_LENGTH} chars, or if no band
     *                                  exists for {@code bandId}
     */
    public Composition create(CreateCompositionCommand cmd, Long bandId) {
        Band band = requireBand(bandId);

        String title = cmd.getTitle();
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Tytuł jest wymagany");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Tytuł utworu może mieć maksymalnie " + MAX_TITLE_LENGTH + " znaków");
        }

        return repository.save(Composition.create(
                title,
                cmd.getDescription(),
                cmd.getComposer(),
                cmd.getArranger(),
                band));
    }

    // ---- update ----------------------------------------------------------

    /**
     * Partial update of the textual metadata: a {@code null} argument leaves that field
     * unchanged, so UI partial-submissions preserve existing values.
     */
    public Composition update(Long id, String title, String description,
                              String composer, String arranger, Long bandId) {
        Composition composition = requireOwned(id, bandId);
        composition.updateTexts(title, description, composer, arranger);
        return repository.save(composition);
    }

    // ---- archive / restore -----------------------------------------------

    /** Move a DRAFT or READY composition into the ARCHIVED state. */
    public void archive(Long id, Long bandId) {
        requireOwned(id, bandId).archive();
    }

    /** Revert an archived composition back to DRAFT (US lifecycle step). */
    public void restore(Long id, Long bandId) {
        requireOwned(id, bandId).restore();
    }

    // ---- failure helpers --------------------------------------------------

    private Band requireBand(Long bandId) {
        return bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + bandId));
    }

    /**
     * Resolve a composition scoped to a band. A miss is a cross-band access attempt or a
     * stale/foreign id, so it maps to {@link IllegalStateException} (→ HTTP 409 Conflict),
     * the project's established multi-tenant conflict idiom. Unknown-band input errors are
     * reserved for {@code create()} where no composition id is involved yet.
     */
    private Composition requireOwned(Long id, Long bandId) {
        return repository.findByIdAndBandId(id, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + id + " does not belong to band " + bandId));
    }
}
