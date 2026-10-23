package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.scoreanalysis.NewFromPdfCommandService;

/**
 * Faza D — "new from PDF" controller: form page, multipart start, 303 confirm redirect.
 */
class NewFromPdfControllerTest {

    private static final long BAND = 1L;
    private static final byte[] BYTES = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    private final NewFromPdfCommandService commandService = mock(NewFromPdfCommandService.class);
    private final WindbandOidcUser user = mock(WindbandOidcUser.class);
    private final MultipartFile file = mock(MultipartFile.class);
    private NewFromPdfController controller;

    @BeforeEach
    void setUp() throws java.io.IOException {
        when(user.getUserId()).thenReturn(42L);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(BYTES);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getOriginalFilename()).thenReturn("Zadzwonił Polonez.pdf");
        controller = new NewFromPdfController(commandService);
    }

    @Test
    @DisplayName("GET → template formularza")
    void pageReturnsTemplate() {
        assertThat(controller.page(BAND)).isEqualTo("compositions/newFromPdf");
    }

    @Test
    @DisplayName("POST /start → multipart przekazywany do serwisu; JSON z id do polling")
    void startForwardsMultipart() throws Exception {
        when(commandService.start(
                BYTES, "application/pdf", "Zadzwonił Polonez.pdf",
                "Mój tytuł", BAND, 42L))
                .thenReturn(new NewFromPdfCommandService.StartResult(5L, 77L, 9L));

        var response = controller.start(BAND, "Mój tytuł", file, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"compositionId\":5")
                .contains("\"fileId\":77")
                .contains("\"analysisId\":9");
    }

    @Test
    @DisplayName("POST /start bez pliku → IAE (400)")
    void startRejectsMissingFile() {
        assertThatThrownBy(() -> controller.start(BAND, null, null, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wybierz plik");
    }

    @Test
    @DisplayName("POST /confirm → 303 See Other z Location na detal utworu")
    void confirmRedirectsToDetail() throws Exception {
        NewFromPdfController.ConfirmBody body = new NewFromPdfController.ConfirmBody(
                5L, "Nowy tytuł", null, null, null);
        when(commandService.confirm(eq(5L), eq(BAND), any(String.class), any(), any(), any(), eq("42")))
                .thenReturn(new NewFromPdfCommandService.ConfirmResult(5L, 2));

        var response = controller.confirm(BAND, body, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo("/bands/1/compositions/5");
    }

    @Test
    @DisplayName("POST /confirm bez compositionId → IAE (400)")
    void confirmRejectsMissingCompositionId() {
        NewFromPdfController.ConfirmBody body = new NewFromPdfController.ConfirmBody(
                null, "T", null, null, null);

        assertThatThrownBy(() -> controller.confirm(BAND, body, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compositionId");
    }
}
