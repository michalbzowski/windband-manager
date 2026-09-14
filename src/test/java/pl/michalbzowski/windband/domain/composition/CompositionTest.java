package pl.michalbzowski.windband.domain.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.band.Band;

/**
 * Pure unit tests for the {@code Composition} aggregate — no Spring context,
 * band is mocked.
 */
class CompositionTest {

    private final Band band1 = mock(Band.class);

    private Composition draft(String title) {
        return Composition.create(title, "desc", "Composer", "Arranger", band1);
    }

    @Nested
    @DisplayName("Creation & invariants")
    class Creation {

        @Test
        @DisplayName("create() produces a DRAFT composition carrying all metadata")
        void create_draft_with_metadata() {
            Composition c = draft("Jabłko");

            assertThat(c.getId()).isNull();                       // assigned at persist time
            assertThat(c.getTitle()).isEqualTo("Jabłko");
            assertThat(c.getDescription()).isEqualTo("desc");
            assertThat(c.getComposer()).isEqualTo("Composer");
            assertThat(c.getArranger()).isEqualTo("Arranger");
            assertThat(c.getStatus()).isEqualTo(CompositionStatus.DRAFT);
            assertThat(c.getBand()).isSameAs(band1);
        }

        @Test
        @DisplayName("create() rejects null / blank titles")
        void rejects_blank_titles() {
            assertThatThrownBy(() -> Composition.create(null, null, null, null, band1))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Composition.create("", null, null, null, band1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Composition.create("   ", null, null, null, band1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("create() rejects a title longer than 200 characters")
        void rejects_oversized_title() {
            String tooLong = "x".repeat(201);
            assertThatThrownBy(() -> Composition.create(tooLong, null, null, null, band1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("200");
        }

        @Test
        @DisplayName("create() requires an owning band (band isolation)")
        void requires_band() {
            assertThatThrownBy(() -> Composition.create("T", null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Partial updates")
    class Updates {

        @Test
        @DisplayName("updateTexts() applies only non-null arguments, preserves the rest")
        void partial_update() {
            Composition c = draft("Old");

            c.updateTexts(null, "new-desc", null, "new-arranger");

            assertThat(c.getTitle()).isEqualTo("Old");            // untouched
            assertThat(c.getDescription()).isEqualTo("new-desc");
            assertThat(c.getComposer()).isEqualTo("Composer");    // untouched
            assertThat(c.getArranger()).isEqualTo("new-arranger");
        }

        @Test
        @DisplayName("updateTexts() re-validates the title when it is provided")
        void updateRejectsBlankTitle() {
            Composition c = draft("Ok");
            assertThatThrownBy(() -> c.updateTexts("   ", null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("updateTexts() rejects titles longer than 200 characters")
        void updateRejectsOversizedTitle() {
            Composition c = draft("Ok");
            String tooLong = "x".repeat(201);
            assertThatThrownBy(() -> c.updateTexts(tooLong, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("200");
        }

        @Test
        @DisplayName("updateTexts() with all-null arguments leaves the aggregate unchanged")
        void allNullIsNoOp() {
            Composition c = draft("Keep");
            String before = c.getDescription();

            c.updateTexts(null, null, null, null);

            assertThat(c.getTitle()).isEqualTo("Keep");
            assertThat(c.getDescription()).isEqualTo(before);
            assertThat(c.getComposer()).isEqualTo("Composer");
        }
    }

    @Nested
    @DisplayName("Lifecycle transitions")
    class Lifecycle {

        @Test
        @DisplayName("archive() -> restore() returns the aggregate to DRAFT (re-editable state)")
        void archiveRestore() {
            Composition c = draft("T");
            assertThat(c.getStatus()).isEqualTo(CompositionStatus.DRAFT);

            c.archive();
            assertThat(c.getStatus()).isEqualTo(CompositionStatus.ARCHIVED);
            assertThat(c.isArchived()).isTrue();

            c.restore();
            assertThat(c.getStatus()).isEqualTo(CompositionStatus.DRAFT);
            assertThat(c.isArchived()).isFalse();
        }

        @Test
        @DisplayName("markReady() sets READY (gate enforced by the command service)")
        void markReady() {
            Composition c = draft("T");
            c.markReady();
            assertThat(c.getStatus()).isEqualTo(CompositionStatus.READY);
        }

        @Test
        @DisplayName("restoring can be immediately promoted to READY via markReady()")
        void restoreThenMarkReady() {
            Composition c = draft("T");
            c.archive();
            c.restore();
            c.markReady();
            assertThat(c.getStatus()).isEqualTo(CompositionStatus.READY);
        }
    }

    @Nested
    @DisplayName("JPA mapping")
    class Mapping {

        @Test
        @DisplayName("band is a LAZY @ManyToOne joined on band_id (nullable=false)")
        void bandMapping() throws Exception {
            Field f = Composition.class.getDeclaredField("band");
            ManyToOne m2o = f.getAnnotation(ManyToOne.class);
            JoinColumn jc = f.getAnnotation(JoinColumn.class);

            assertThat(m2o).isNotNull();
            assertThat(m2o.fetch()).isEqualTo(FetchType.LAZY);
            assertThat(jc).isNotNull();
            assertThat(jc.name()).isEqualTo("band_id");
            assertThat(jc.nullable()).isFalse();
        }
    }
}
