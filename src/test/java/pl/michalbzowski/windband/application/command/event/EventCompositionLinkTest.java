package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventComposition;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-7.2 - composition to event link (the "Skad repertuaru" feature), contract-level tests.
 * Drives EventCommandService directly on BaseIntegrationTest (Postgres + data.sql, band id=1 pre-seeded).
 * Verifies:
 *   1) happy path — two same-band compositions land at order_in_set 1 and 2 of the event's setlist;
 *   2) cross-band refusal — a composition of another band cannot attach (IllegalStateException + no row);
 *   3) unassign is idempotent (safe to call twice on an already-removed pair).
 */
class EventCompositionLinkTest extends BaseIntegrationTest {

    @Autowired private EventCommandService eventCommandService;
    @Autowired private BandRepository bandRepository;
    @Autowired private pl.michalbzowski.windband.application.command.composition.CompositionCommandService compositionCommandService;
    @Autowired private pl.michalbzowski.windband.domain.event.EventRepository eventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM event_compositions");
        jdbcTemplate.update("DELETE FROM band_events");
    }

    private Long seedCompositionInBand(Long bandId, String title) {
        Band band = bandRepository.findById(bandId).orElseThrow();
        pl.michalbzowski.windband.application.command.composition.CreateCompositionCommand cmd =
                new pl.michalbzowski.windband.application.command.composition.CreateCompositionCommand();
        cmd.setTitle(title);
        cmd.setDescription("");
        cmd.setComposer("Test Composer");
        cmd.setArranger("Test Arranger");

        // Save via the service, which resolves the band itself (idempotent for same-band test).
        Composition saved = compositionCommandService.create(cmd, bandId);
        return saved.getId();
    }

    private BandEvent seedEventInBand(Long bandId, String name) {
        Band band = bandRepository.findById(bandId).orElseThrow();
        BandEvent event = BandEvent.create(
                name, LocalDate.now().plusDays(30), LocalTime.of(18, 0),
                "Rynek", pl.michalbzowski.windband.domain.event.EventType.CONCERT,
                band, pl.michalbzowski.windband.domain.event.PaymentType.FREE, null);
        return eventRepository.save(event);
    }

    @Test
    void assignComposition_twoSameBandItems_landInSetlistOrder() {
        Band band = bandRepository.findById(1L).orElseThrow();
        Long compA = seedCompositionInBand(band.getId(), "Piece A US-7.2");
        Long compB = seedCompositionInBand(band.getId(), "Piece B US-7.2");

        BandEvent event = seedEventInBand(band.getId(), "Koncert US-7.2");

        EventComposition rowA = eventCommandService.assignComposition(event.getId(), compA);
        assertThat(rowA.getOrderInSet()).isEqualTo(1);

        EventComposition rowB = eventCommandService.assignComposition(event.getId(), compB);
        assertThat(rowB.getOrderInSet()).isEqualTo(2);

        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ?", Long.class, event.getId());
        assertThat(count).isEqualTo(2L);

        // Re-adding an already-linked composition ("move to back") reorders it — no duplicate row.
        eventCommandService.assignComposition(event.getId(), compA);
        count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ?", Long.class, event.getId());
        assertThat(count).isEqualTo(2L); // still 2 (re-use row, not new)
    }

    @Test
    void assignComposition_crossBand_refusedAndNoRow() {
        // Seed a foreign band + a composition in it.
        Band foreignBand = bandRepository.save(Band.create("ForeignBand US-7.2", "foreign-band-us-7-2"));
        Long compForeign = seedCompositionInBand(foreignBand.getId(), "Piece In ForeignBand");

        Band homeBand = bandRepository.findById(1L).orElseThrow();
        BandEvent event = seedEventInBand(homeBand.getId(), "Event Cross");

        assertThatThrownBy(() -> eventCommandService.assignComposition(event.getId(), compForeign))
                .isInstanceOf(IllegalStateException.class);

        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ?", Long.class, event.getId());
        assertThat(count).isEqualTo(0L); // cross-band row refused — no persistent link
    }

    @Test
    void unassignComposition_idempotentAndRemovesTheRow() {
        Band band = bandRepository.findById(1L).orElseThrow();
        Long compA = seedCompositionInBand(band.getId(), "Piece Un US-7.2");
        BandEvent event = seedEventInBand(band.getId(), "Event Unassign US-7.2");

        eventCommandService.assignComposition(event.getId(), compA);
        long beforeDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ?", Long.class, event.getId());
        assertThat(beforeDelete).isEqualTo(1L);

        // Unlink twice on the same pair — must not throw (idempotent delete) and must leave 0 rows.
        eventCommandService.unassignComposition(event.getId(), compA);
        eventCommandService.unassignComposition(event.getId(), compA);

        long after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ?", Long.class, event.getId());
        assertThat(after).isEqualTo(0L);
    }
}
