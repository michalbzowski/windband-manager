package pl.michalbzowski.windband.application.command.composition;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;

import java.util.Optional;

/**
 * Write side of the {@code instrument_role_map} admin surface (US-7.3): add / remove a band's
 * tag→role mapping rows from inside the app, replacing hand-written SQL (US-1.4 left the
 * persistence in place exactly for this seam).
 *
 * <p><b>Isolation contract:</b> every mutating operation is scoped to the band resolved via
 * {@link BandQueryService#getRequiredBand(Long)}; a mapping owned by another band can never be
 * created or deleted through an endpoint of a different team. Duplicate rows are rejected at
 * the application level (same case-insensitive-tag + exact-role contract as the V39 unique
 * index), and cross-band / unknown deletes fail closed with {@link IllegalStateException}
 * (→ HTTP 409) without touching any row.
 */
@Service
@RequiredArgsConstructor
public class InstrumentRoleMapCommandService {

    private final BandQueryService bandQueryService;
    private final InstrumentRoleMapRepository instrumentRoleMapRepository;

    /**
     * Adds a new mapping for {@code bandId}. Blank / null tag or role are rejected by the
     * entity factory with {@link IllegalArgumentException} (→ HTTP 400); a row that already
     * exists for the same logical tag + exact role is rejected application-side before it can
     * trip the DB unique index.
     */
    @Transactional
    public InstrumentRoleMap addMapping(Long bandId, String sourceTag, String targetRolePattern, String description) {
        Band band = bandQueryService.getRequiredBand(bandId);
        // The optional admin note is an empty string when the form field is left blank — the
        // entity factory rejects blank-but-set descriptions, so normalise to null here.
        String note = (description == null || description.isBlank()) ? null : description.trim();
        String role = targetRolePattern == null ? null : targetRolePattern.trim();
        boolean duplicate = instrumentRoleMapRepository.existsByBandIdAndSourceTagAndTargetRolePattern(
                bandId, sourceTag, role);
        if (duplicate) {
            throw new IllegalArgumentException("Mapowanie '" + trimmed(sourceTag) + "' → '" + trimmed(role)
                    + "' już istnieje w tym zespole");
        }
        return instrumentRoleMapRepository.save(
                InstrumentRoleMap.forBand(band, sourceTag, targetRolePattern, note));
    }

    /**
     * Deletes the mapping row {@code id} — but ONLY when it belongs to {@code bandId}. A row
     * owned by another band (or an unknown id) fails closed with {@link IllegalStateException}
     * (→ HTTP 409): no cross-band delete, no existence oracle beyond the 404/409 pair.
     */
    @Transactional
    public void deleteMapping(Long bandId, Long mappingId) {
        // Fail closed on an unknown band (→ HTTP 400) before any delete is even considered.
        bandQueryService.getRequiredBand(bandId);
        Optional<InstrumentRoleMap> owned = instrumentRoleMapRepository.findAllByBandId(bandId).stream()
                .filter(mapping -> mapping.getId().equals(mappingId))
                .findFirst();
        if (owned.isEmpty()) {
            throw new IllegalStateException("Nie znaleziono mapowania o ID " + mappingId + " w zespole o ID " + bandId);
        }
        instrumentRoleMapRepository.delete(owned.get());
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
