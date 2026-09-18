package pl.michalbzowski.windband.application.command.composition;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;

import java.time.Instant;
import java.util.List;

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
    private final CompositionInstrumentRepository instrumentRepository;

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
        Composition composition = requireOwned(id, bandId);
        composition.archive();
        repository.save(composition);
    }

    /** Revert an archived composition back to DRAFT (US lifecycle step). */
    public void restore(Long id, Long bandId) {
        Composition composition = requireOwned(id, bandId);
        composition.restore();
        repository.save(composition);
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

    // ---- verify-gate (US-3.03) -------------------------------------------

    /**
     * Verifies every unverified part of the composition as authored by {@code verifier}
     * and transitions the composition to {@code READY} once no part remains unverified.
     *
     * <p>Idempotent for already-verified rows: {@link CompositionInstrument#verify(String, Instant)}
     * freezes the audit pair on first call, so a second pass (or a re-run of this method after
     * an earlier partial verification) cannot hijack another user's {@code verifiedBy}/{@code verifiedAt}.
     * A caller with an unverified parts list simply completes the missing ones and promotes — no row is
     * ever reset or overwritten.
     *
     * <p><b>Error semantics</b> (consistent with this service's existing contract):
     * blank {@code verifier} → {@link IllegalArgumentException} (HTTP 400, pure input error);
     * unknown or foreign-band composition id → {@link IllegalStateException} (HTTP 409 fail-closed) — no part row
     * is read, modified, or saved in either failure path.
     */
    public void verifyCompositionParts(Long id, Long bandId, String verifier) {
        if (verifier == null || verifier.isBlank()) {
            throw new IllegalArgumentException("verifier required");
        }
        Composition composition = requireOwned(id, bandId);
        List<CompositionInstrument> parts = instrumentRepository.findAllByComposition(composition);
        Instant now = Instant.now();
        for (CompositionInstrument part : parts) {
            if (part.getVerifiedBy() == null) {
                part.verify(verifier.trim(), now);
            }
        }
        boolean anyUnverified = parts.stream().anyMatch(p -> p.getVerifiedBy() == null);
        if (!anyUnverified) {
            composition.markReady();
        }
        repository.save(composition); // dirty-check persists the part rows + (potentially) the status flip
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
