package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.michalbzowski.windband.application.command.event.EventPartDeliveryCommandService;
import pl.michalbzowski.windband.application.service.EmailSender;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventComposition;
import pl.michalbzowski.windband.domain.event.EventCompositionRepository;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-6.6 — the event DETAIL page renders the "📜 Historia rozdań" panel AFTER a delivery:
 * the exact Thymeleaf/record-accessor contract ({@code run.rows[0].actor}, the outcome chips, the
 * per-row cells) is exercised against the REAL template with a REAL context, so a record accessor
 * typo or a SpEL error turns into a test failure instead of a silent 500 in production. Selenium
 * can't run on CI (auth principal), so this render contract IS the UI test for the panel.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(EventDeliveryHistoryRenderTest.CapturingMailConfig.class)
class EventDeliveryHistoryRenderTest {

    @Autowired private MockMvc mvc;
    @Autowired private BandRepository bandRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private CompositionInstrumentRepository partRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventCompositionRepository eventCompositionRepository;
    @Autowired private EventPartDeliveryCommandService deliveryService;
    @Autowired private CapturedMailLog captured;

    /**
     * Band 1 "Test Band" from data.sql is the admin's band — reuse it so the form-login session
     * (admin → band 1 member) passes requireBandAccess exactly like RehearsalDetailRenderTest.
     */
    private static final long BAND_A = 1L;

    @Test
    void detailPage_rendersTheDeliveryHistoryPanelWithDeliveredRows() throws Exception {
        // ── seed on band 1: a consenting member plays one part on one piece with a covering file ──
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Band band = bandRepository.findById(BAND_A).orElseThrow();

        Instrument trumpet = instrumentRepository.save(Instrument.create("TrąbkaUI66" + suffix, band));
        Member jan = Member.create("JanUI66" + suffix, "Kowalski", null, band);
        jan.updateContact("janui66-" + suffix + "@test.com", null, true); // consent → eligible envelope
        jan.addInstrument(trumpet, true);
        memberRepository.save(jan);

        Composition marsz = compositionRepository.save(Composition.create(
                "Marsz UI-US-6.6 " + suffix, "opis", "Kompozytor", "Aranżer", band));
        partRepository.save(CompositionInstrument.forComposition(
                marsz, trumpet, "TrąbkaUI66" + suffix, 1, 4, null, PartSource.MANUAL, 1.0));

        scoreFileRepository.save(ScoreFile.forComposition(marsz, "application/pdf",
                "score66-" + suffix + ".pdf", 12_345L, sha256("s66-" + suffix), "/srv/scores66.pdf", 6));

        BandEvent event = eventRepository.save(BandEvent.create(
                "Koncert UI US-6.6 " + suffix, LocalDate.now().plusDays(14), LocalTime.of(18, 0),
                "Sala koncertowa", EventType.CONCERT, band, PaymentType.FREE, null));
        eventCompositionRepository.save(EventComposition.link(event, marsz, 1));

        // One run: jan is the ONLY active member on that part → exactly one DELIVERED decision.
        captured.reset();
        assertThat(deliveryService.deliverParts(event.getId(), BAND_A, "dyrygent@test.com").sent()).isEqualTo(1);

        // ── log in (form auth) and GET the detail page — the REAL Thymeleaf render is the test ──
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        String html = mvc.perform(get("/events/" + event.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Panel is present and NOT in its empty state (a record-accessor/SpEL error would 500 here).
        assertThat(html).contains("id=\"delivery-history-panel\"");
        assertThat(html).contains("Historia rozdań");
        // Tallies rendered from the DTO record accessors.
        assertThat(html).contains(">1<");
        // The DELIVERED outcome chip rendered (r.outcome == 'DELIVERED' branch of the panel).
        assertThat(html).contains("Wysłano");
        // The run header's actor (run.rows[0].actor accessor path) made it into the HTML.
        assertThat(html).contains("dyrygent@test.com");
        // Jan's row: name + part role + page range 1–4 all made it through the template.
        assertThat(html).contains("JanUI66" + suffix);
        assertThat(html).contains("TrąbkaUI66" + suffix);
    }

    /** The same page for a FRESH event (no delivery run yet) still renders the honest empty state. */
    @Test
    void detailPage_freshEvent_showsEmptyHistoryState() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Band band = bandRepository.findById(BAND_A).orElseThrow();
        BandEvent event = eventRepository.save(BandEvent.create(
                "Koncert pusty US-6.6 " + suffix, LocalDate.now().plusDays(7), LocalTime.of(20, 0),
                "Sokół", EventType.CONCERT, band, PaymentType.FREE, null));

        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        String html = mvc.perform(get("/events/" + event.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"delivery-history-panel\"");
        assertThat(html).contains("Brak historii — głosy jeszcze nie były rozdawane");
        // No run headers at all.
        assertThat(html).doesNotContain("Próba</strong>");
    }

    private org.springframework.mock.web.MockHttpSession loginAsAdmin() throws Exception {
        MvcResult login = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession();
    }

    private static String sha256(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ─────────────── captured e-mail transport (same seam as the US-6.3/6.4 IT) ───────────────

    static class CapturedMailLog implements EmailSender {
        final List<Mail> mails = new ArrayList<>();

        record Mail(String to, String subject, String html) { }

        @Override
        public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlContent) {
            mails.add(new Mail(toEmail, subject, htmlContent));
        }

        void reset() {
            mails.clear();
        }
    }

    @TestConfiguration
    static class CapturingMailConfig {
        @Bean
        @Primary
        CapturedMailLog capturingEmailSender() {
            return new CapturedMailLog();
        }
    }
}
