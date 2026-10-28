package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression (user report, Oct 2026): editing an existing tag — bumping {@code sortPriority}
 * by +1 in {@code /tags} and clicking "Zapisz" — died with
 * {@code Could not write JSON: could not initialize proxy [pl.michalbzowski.windband.domain.band.Band#2] - no Session}.
 *
 * <p>Root cause: {@code PUT /api/tags/{id}} returned the {@link Instrument} <em>entity</em>.
 * The entity carries LAZY {@code band} / {@code aliasOf} associations; the command service checks
 * scope via {@code getBand().getId()} (which does NOT initialise the proxy), and with
 * {@code spring.jpa.open-in-view: false} Jackson then serialises an uninitialised proxy outside
 * any session. The fix is the HTTP contract this test pins: the REST layer answers with a flat
 * DTO — scalars only, no association fields — for every tag/instrument endpoint.</p>
 *
 * <p>Uses the real Spring context + H2 so the failure mode above is genuinely reproduced:
 * a mocked service could never expose a Hibernate proxy. The seeded row owns its band (#1,
 * the admin's active team, mirroring the reported Band#2 case) and is deleted after the test.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TagEditLazyBandRegressionTest {

    private static final long SEED_BAND_ID = 1L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    private MockHttpSession session;
    private Instrument seededTag;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "admin")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        session = (MockHttpSession) login.getRequest().getSession();

        // Seed exactly like the bug report: an existing tag that belongs to a band, so its
        // `band` association is a lazy proxy on the next load. UUID suffix dodges the
        // (band_id, name) unique constraint across the shared H2 context.
        Band band = bandRepository.findById(SEED_BAND_ID).orElseThrow();
        String name = "Lazy-edit regression tag " + UUID.randomUUID();
        seededTag = instrumentRepository.save(Instrument.create(name, band));
    }

    @AfterEach
    void removeSeededTag() {
        instrumentRepository.findById(seededTag.getId())
                .ifPresent(instrumentRepository::delete);
    }

    @Test
    @DisplayName("PUT /api/tags/{id} z +1 do kolejności zapisuje tag i zwraca DTO bez proxy asocjacji")
    void editTagSortPriority_serializesWithoutLazyBandProxy() throws Exception {
        int bumpedPriority = seededTag.getSortPriority() + 1;

        mvc.perform(put("/api/tags/" + seededTag.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + seededTag.getName() + "\","
                                + "\"description\":\"kolejność przesunięta o jeden\","
                                + "\"sortPriority\":" + bumpedPriority + "}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(seededTag.getId()))
                .andExpect(jsonPath("$.name").value(seededTag.getName()))
                .andExpect(jsonPath("$.sortPriority").value(bumpedPriority))
                // The entity graph must never leak into the payload — this is what blew up.
                .andExpect(jsonPath("$.band").doesNotExist())
                .andExpect(jsonPath("$.aliasOf").doesNotExist());
    }

    @Test
    @DisplayName("GET i PUT /api/instruments/{id} mają ten sam kontrakt DTO (bliźniacza endpoindra)")
    void instrumentsEndpoints_shareDtoContract() throws Exception {
        int bumpedPriority = seededTag.getSortPriority() + 1;

        mvc.perform(get("/api/instruments")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + seededTag.getId() + ")].name")
                        .value(org.hamcrest.Matchers.hasItem(seededTag.getName())))
                .andExpect(jsonPath("$[0].aliasOf").doesNotExist());

        mvc.perform(put("/api/instruments/" + seededTag.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + seededTag.getName() + "\","
                                + "\"description\":null,"
                                + "\"sortPriority\":" + bumpedPriority + "}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortPriority").value(bumpedPriority))
                .andExpect(jsonPath("$.band").doesNotExist())
                .andExpect(jsonPath("$.aliasOf").doesNotExist());
    }
}
