package pl.michalbzowski.windband.domain.composition;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import pl.michalbzowski.windband.domain.band.Band;

/**
 * US-4.1 — ScoreAnalysis state machine (pure unit test, no Spring).
 * Every legal + illegal transition is covered to pin down the invariant set
 * that the Flyway CHECK constraints also enforce at the DB level.
 */
class ScoreAnalysisTest {

    private static Composition comp() {
        // The state machine only stores a reference — never dereferences it — so any
        // non-null ref satisfies the NPE contract. This keeps the test free of DB setup.
        return Composition.create("t", null, null, null, Band.create("test band", "test-band"));
    }

    @Test
    void pending_requires_composition() {
        assertThatThrownBy(() -> ScoreAnalysis.pending(null, 42L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void pending_requires_scoreFileId() {
        assertThatThrownBy(() -> ScoreAnalysis.pending(comp(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void pending_state_is_initial_phase_PENDING_with_nulls() {
        ScoreAnalysis row = ScoreAnalysis.pending(comp(), 42L);
        assertThat(row.getPhase()).isEqualTo(ScoreAnalysis.Phase.PENDING);
        assertThat(row.isTerminal()).isFalse();
        assertThat(row.getRunnerRef()).isNull();
        assertThat(row.getFinishedAt()).isNull();
    }

    @Test
    void markRunning_from_PENDING_is_legal_and_sets_phase_and_runnerRef() {
        ScoreAnalysis next = ScoreAnalysis.pending(comp(), 42L).markRunning("pid-7");
        assertThat(next.getPhase()).isEqualTo(ScoreAnalysis.Phase.RUNNING);
        assertThat(next.getRunnerRef()).isEqualTo("pid-7");
        // The row is NOT terminal and finishedAt stays null until a terminal transition.
        assertThat(next.isTerminal()).isFalse();
        assertThat(next.getFinishedAt()).isNull();
    }

    @Test
    void markRunning_from_SUCCEEDED_is_illegal() {
        ScoreAnalysis s = ScoreAnalysis.pending(comp(), 42L)
                .markRunning("pid-7")
                .succeed("a", "b", "c", "d");
        assertThatThrownBy(() -> s.markRunning("again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void succeed_from_RUNNING_populates_all_artefact_paths_and_terminal() {
        ScoreAnalysis ok = ScoreAnalysis.pending(comp(), 42L)
                .markRunning("pid-7")
                .succeed("/a.json", "/a.musicxml", "/a.mid", "/v.txt");
        assertThat(ok.getPhase()).isEqualTo(ScoreAnalysis.Phase.SUCCEEDED);
        assertThat(ok.isTerminal()).isTrue();
        assertThat(ok.getArrangementJsonPath()).isEqualTo("/a.json");
        assertThat(ok.getArrangementMusicxmlPath()).isEqualTo("/a.musicxml");
        assertThat(ok.getArrangementMidPath()).isEqualTo("/a.mid");
        assertThat(ok.getValidationTxtPath()).isEqualTo("/v.txt");
        assertThat(ok.getFinishedAt()).isNotNull();
    }

    @Test
    void succeed_from_PENDING_is_illegal() {
        ScoreAnalysis p = ScoreAnalysis.pending(comp(), 42L);
        assertThatThrownBy(() -> p.succeed("a", "b", "c", "d"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_from_RUNNING_requires_non_blank_message() {
        ScoreAnalysis r = ScoreAnalysis.pending(comp(), 42L).markRunning("pid-7");
        assertThatThrownBy(() -> r.fail(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.fail("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fail_from_RUNNING_populates_terminal_with_polish_message() {
        ScoreAnalysis failed = ScoreAnalysis.pending(comp(), 42L)
                .markRunning("pid-7")
                .fail("Pipeline AI nie skonfigurowany");
        assertThat(failed.getPhase()).isEqualTo(ScoreAnalysis.Phase.FAILED);
        assertThat(failed.isTerminal()).isTrue();
        assertThat(failed.getErrorMessage()).isEqualTo("Pipeline AI nie skonfigurowany");
    }

    @Test
    void fail_from_FAILED_is_illegal() {
        ScoreAnalysis f = ScoreAnalysis.pending(comp(), 42L)
                .markRunning("p").fail("first");
        assertThatThrownBy(() -> f.fail("again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markRunning_from_RUNNING_is_illegal() {
        ScoreAnalysis r = ScoreAnalysis.pending(comp(), 42L).markRunning("p1");
        assertThatThrownBy(() -> r.markRunning("p2"))
                .isInstanceOf(IllegalStateException.class);
    }
}
