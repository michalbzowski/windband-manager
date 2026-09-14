package pl.michalbzowski.windband.application.command.composition;

/**
 * Signals that a composition could not be resolved in the context of the given band.
 * Carries both the composition id and the band id so callers (controllers, cron jobs)
 * can build precise user-facing errors without re-querying.
 */
public class CompositionNotFoundException extends RuntimeException {

    private final Long compositionId;
    private final Long bandId;

    public CompositionNotFoundException(Long compositionId, Long bandId) {
        super("Composition not found for given band");
        this.compositionId = compositionId;
        this.bandId = bandId;
    }

    public long getCompositionId() {
        return compositionId;
    }

    public long getBandId() {
        return bandId;
    }
}
