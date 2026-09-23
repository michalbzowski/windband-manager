package pl.michalbzowski.windband.application.query.composition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.command.composition.PdfPageExtractor;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.PartShareToken;
import pl.michalbzowski.windband.domain.composition.PartShareTokenRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * US-7.11 — defensive invariants of {@link PublicPartLinkQueryService} that the shared real-database
 * IT cannot express: {@code score_files.storage_path} is {@code NOT NULL} in both the Flyway DDL
 * (V35) and Hibernate's, so a DB row can never carry a null path. This class pins the guard at the
 * unit level regardless — the public read path must fail closed as a uniform 404 and never leak an
 * NPE→500 to an anonymous recipient.
 */
@ExtendWith(MockitoExtension.class)
class PublicPartLinkQueryServiceTest {

    @Test
    @DisplayName("bound score file with a null storage path fails closed (uniform 404), never Path.of(null) → NPE → 500")
    void nullStoragePath_failsClosed_notServer500() {
        UUID token = UUID.randomUUID();

        CompositionInstrument part = mock(CompositionInstrument.class);
        ScoreFile bound = mock(ScoreFile.class);
        when(part.getScoreFile()).thenReturn(bound);
        // Passes the covering gate (pageCount=5 >= pageTo=3) — so resolution would pick this file,
        // and only then would it meet the impossible null path.
        when(bound.getPageCount()).thenReturn(5);
        when(bound.getStoragePath()).thenReturn(null);
        when(part.getPageFrom()).thenReturn(2);
        when(part.getPageTo()).thenReturn(3);
        when(part.getComposition()).thenReturn(mock(Composition.class));

        PartShareToken share = mock(PartShareToken.class);
        when(share.getPart()).thenReturn(part);

        PartShareTokenRepository tokenRepository = mock(PartShareTokenRepository.class);
        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(share));
        ScoreFileRepository scoreFileRepository = mock(ScoreFileRepository.class);

        PublicPartLinkQueryService service = new PublicPartLinkQueryService(
                tokenRepository, scoreFileRepository, new PdfPageExtractor());

        assertThatThrownBy(() -> service.openByToken(token))
                .isInstanceOf(PublicPartLinkQueryService.TokenNotFoundException.class);
    }

    @Test
    @DisplayName("null token fails closed before any repository access")
    void nullToken_failsClosed() {
        PartShareTokenRepository tokenRepository = mock(PartShareTokenRepository.class);
        ScoreFileRepository scoreFileRepository = mock(ScoreFileRepository.class);

        PublicPartLinkQueryService service = new PublicPartLinkQueryService(
                tokenRepository, scoreFileRepository, new PdfPageExtractor());

        assertThatThrownBy(() -> service.openByToken(null))
                .isInstanceOf(PublicPartLinkQueryService.TokenNotFoundException.class);
    }

    @Test
    @DisplayName("unknown token fails closed (uniform 404, no existence oracle)")
    void unknownToken_failsClosed() {
        UUID token = UUID.randomUUID();
        PartShareTokenRepository tokenRepository = mock(PartShareTokenRepository.class);
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());
        ScoreFileRepository scoreFileRepository = mock(ScoreFileRepository.class);

        PublicPartLinkQueryService service = new PublicPartLinkQueryService(
                tokenRepository, scoreFileRepository, new PdfPageExtractor());

        assertThatThrownBy(() -> service.openByToken(token))
                .isInstanceOf(PublicPartLinkQueryService.TokenNotFoundException.class);
    }

    @Test
    @DisplayName("vanishing file on disk → uniform 404 (deliberately NOT 409: indistinguishable from a revoked link)")
    void missingFile_isNotFound_notConflict() {
        UUID token = UUID.randomUUID();

        CompositionInstrument part = mock(CompositionInstrument.class);
        ScoreFile bound = mock(ScoreFile.class);
        when(part.getScoreFile()).thenReturn(bound);
        when(bound.getPageCount()).thenReturn(5);                 // clears the covering gate
        when(bound.getStoragePath()).thenReturn("/tmp/windband/does-not-exist-9f2c.pdf");
        when(part.getPageFrom()).thenReturn(2);
        when(part.getPageTo()).thenReturn(3);
        Composition composition = mock(Composition.class);
        when(part.getComposition()).thenReturn(composition);

        PartShareToken share = mock(PartShareToken.class);
        when(share.getPart()).thenReturn(part);

        PartShareTokenRepository tokenRepository = mock(PartShareTokenRepository.class);
        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(share));
        ScoreFileRepository scoreFileRepository = mock(ScoreFileRepository.class);
        when(scoreFileRepository.findAllByComposition(composition)).thenReturn(List.of());

        PublicPartLinkQueryService service = new PublicPartLinkQueryService(
                tokenRepository, scoreFileRepository, new PdfPageExtractor());

        // A vanishing file is a uniform 404 (fail closed) — deliberately NOT the covering-file
        // 409 semantics; the true slice==null → 409 case is covered by PartShareTokenIT.
        assertThatThrownBy(() -> service.openByToken(token))
                .isInstanceOf(PublicPartLinkQueryService.TokenNotFoundException.class);
    }
}
