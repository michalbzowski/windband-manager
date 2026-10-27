package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue #269 — adding a tag whose name is already used produced HTTP 500.
 *
 * <p>Measured on {@code origin/main} with a probe (duplicate "Trąbka" in the same band):
 * the command service saved straight through, the DB unique index
 * {@code instruments_band_id_name_key} rejected the row and the raw
 * {@code DataIntegrityViolationException}/{@code PSQLException} escaped to the
 * catch-all handler — 500, "duplicate key" wording, nothing the user could act on.
 *
 * <p>The probe also settled the other two claims in the report: the same name in a
 * <em>different</em> band and a name reused <em>after deleting</em> the old tag both
 * succeeded already, so those are not bugs — the guard below must not break them.
 *
 * <p>Contract now:
 * <ul>
 *   <li>duplicate (case-insensitive) name inside the band → {@link DuplicateInstrumentNameException},
 *       which extends {@link IllegalStateException} and is therefore mapped to 409 Conflict by
 *       {@code GlobalExceptionHandler}, with a Polish message naming the tag;</li>
 *   <li>the same name in another band and reuse after deletion stay allowed;</li>
 *   <li>renaming a tag to its own name (even in another case) is not a self-collision.</li>
 * </ul>
 */
class InstrumentDuplicateNameTest extends BaseIntegrationTest {

    @Autowired
    private InstrumentCommandService commandService;

    @Autowired
    private BandRepository bandRepository;

    private Long band1Id;
    private Long band2Id;

    @BeforeEach
    void setUp() {
        band1Id = 1L;
        Band band2 = bandRepository.save(Band.create("Zespół D #269 " + System.nanoTime(), "zespold-" + System.nanoTime()));
        band2Id = band2.getId();
    }

    @Test
    void duplicateNameInSameBandThrowsReadableConflictInsteadOfDatabaseError() {
        String name = "Koncert Mistrz " + System.nanoTime();
        commandService.createInstrument(name, "pierwszy", 10, band1Id);

        assertThatThrownBy(() -> commandService.createInstrument(name, "drugi", 11, band1Id))
                .isInstanceOf(InstrumentCommandService.DuplicateInstrumentNameException.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name)
                .hasMessageContaining("już istnieje");
    }

    @Test
    void duplicateIsDetectedRegardlessOfLetterCase() {
        String name = "Kapelmistrzu " + System.nanoTime();
        commandService.createInstrument(name, "", 12, band1Id);

        assertThatThrownBy(() -> commandService.createInstrument(name.toUpperCase(), "", 13, band1Id))
                .as("Flet / FLET is the same tag for the user")
                .isInstanceOf(InstrumentCommandService.DuplicateInstrumentNameException.class);
    }

    @Test
    void surroundingSpacesDoNotSlipPastTheGuard() {
        String name = "Koncert Mistrz " + System.nanoTime();
        commandService.createInstrument(name, "", 14, band1Id);

        assertThatThrownBy(() -> commandService.createInstrument("  " + name + "  ", "", 15, band1Id))
                .as("' Nazwa ' and 'Nazwa' are the same tag")
                .isInstanceOf(InstrumentCommandService.DuplicateInstrumentNameException.class);

        String spaced = "Przeciągniety " + System.nanoTime();
        assertThat(commandService.createInstrument("  " + spaced + "  ", "", 16, band1Id).getName())
                .as("the stored name is trimmed, so the list and the guard agree")
                .isEqualTo(spaced);
    }

    @Test
    void sameNameInAnotherBandStaysAllowed() {
        String name = "Wspolna Nazwa " + System.nanoTime();
        commandService.createInstrument(name, "band 1", 20, band1Id);

        assertThatCode(() -> commandService.createInstrument(name, "band 2", 21, band2Id))
                .as("bands are isolated — the report's 'used in another team' case must not be blocked")
                .doesNotThrowAnyException();
    }

    @Test
    void nameOfADeletedTagCanBeUsedAgain() {
        String name = "Do Usuniecia " + System.nanoTime();
        Instrument created = commandService.createInstrument(name, "", 30, band1Id);
        commandService.deleteInstrument(created.getId(), band1Id);

        assertThatCode(() -> commandService.createInstrument(name, "po usunieciu", 31, band1Id))
                .as("deleting a tag frees its name — the 'used in the past' case must not be blocked")
                .doesNotThrowAnyException();
    }

    @Test
    void renameOntoAnotherTagsNameIsRejectedAndOwnNameIsAccepted() {
        String first = "Pierwszy " + System.nanoTime();
        String second = "Drugi " + System.nanoTime();
        Instrument a = commandService.createInstrument(first, "", 40, band1Id);
        Instrument b = commandService.createInstrument(second, "", 41, band1Id);

        assertThatThrownBy(() -> commandService.updateInstrument(b.getId(), first, "", 41, band1Id))
                .isInstanceOf(InstrumentCommandService.DuplicateInstrumentNameException.class);

        assertThatCode(() -> commandService.updateInstrument(b.getId(), second.toUpperCase(), "", 42, band1Id))
                .as("a tag may keep its own name in another case — it must not collide with itself")
                .doesNotThrowAnyException();
    }
}
