package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.event.EventCommandService;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.application.command.event.EventPartDeliveryCommandService;
import pl.michalbzowski.windband.application.command.event.NotificationSender;
import pl.michalbzowski.windband.application.dto.event.EventPartDeliveryHistoryDto.DeliveryBlock;
import pl.michalbzowski.windband.application.dto.event.EventPartDeliveryHistoryDto.DeliveryHistory;
import pl.michalbzowski.windband.application.dto.event.EventPartDeliveryHistoryDto.DeliveryRow;
import pl.michalbzowski.windband.application.query.event.EventPartDeliveryQueryService;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-6.6 — contract test for {@code GET /api/events/{id}/part-deliveries}: the read half of the
 * delivery history ("Historia rozdań"), the append-only audit written by the US-6.3/6.4 command
 * path.
 *
 * <p>Pins only what a REST endpoint adds on top of the query-service contract (band isolation,
 * honest counts — covered by their own tests):
 * <ul>
 *   <li>200 + the history envelope — delivered/skipped counters, runs newest-first with every
 *       row's outcome verbatim, so the UI panel renders from one stable payload;</li>
 *   <li>404 for an unknown event (via {@link GlobalExceptionHandler});</li>
 *   <li>409 for a foreign-band event (the band-isolation contract, same as the write path);</li>
 *   <li>400 when there is no active team context — before the service is ever touched.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EventPartDeliveryHistoryRestControllerTest {

    private static final long EVENT_ID = 91L;
    private static final long BAND_ID = 7L;
    private static final long USER_ID = 3L;
    private static final String ACTOR_EMAIL = "dyrygent@bandmanager.pl";

    @Mock
    private EventCommandService commandService;
    @Mock
    private EventQueryService queryService;
    @Mock
    private TeamQueryService teamQueryService;
    @Mock
    private NotificationSender notificationSender;
    @Mock
    private EventPartDeliveryCommandService partDeliveryService;
    @Mock
    private EventPartDeliveryQueryService partDeliveryHistoryService;
    @Mock
    private RestTemplate restTemplate;

    private WindbandOidcUser user;
    /** Mutable per-test principal (null when the endpoint must reject for "no active team"). */
    private final AtomicReference<Object> principalRef = new AtomicReference<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "test-subject-dyrygent");
        claims.put("preferred_username", "dyrygent");
        claims.put("email", ACTOR_EMAIL);
        claims.put("name", "Dyrygent");
        OidcIdToken idToken = new OidcIdToken("mock-token", Instant.now(),
                Instant.now().plusSeconds(3600), claims);
        DefaultOidcUser delegate = new DefaultOidcUser(List.of(), idToken);

        user = new WindbandOidcUser(delegate, USER_ID, "dyrygent", ACTOR_EMAIL,
                true, false, BAND_ID, "test-band", "ADMIN", List.of(BAND_ID));
        principalRef.set(user);

        // Mirror Spring Boot's Jackson defaults: Java 8 date/time support + ISO strings (not
        // epoch numbers), otherwise the Instant in DeliveryBlock/DeliveryRow won't serialize here
        // while it does in production.
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(new EventController(commandService, queryService,
                        teamQueryService, notificationSender, partDeliveryService,
                        partDeliveryHistoryService, restTemplate))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver())
                .build();
    }

    private MockHttpSession freshSession() {
        return new MockHttpSession();
    }

    private HandlerMethodArgumentResolver principalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(OidcUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principalRef.get();
            }
        };
    }

    private ResultActions history(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/events/" + EVENT_ID + "/part-deliveries").session(session));
    }

    // ─────────────────────────────── 200 — the envelope ───────────────────────────────

    @Test
    @DisplayName("US-6.6 history: 200 with honest counters, newest run first, rows verbatim")
    void history_success_returnsRunsNewestFirstWithEveryOutcome() throws Exception {
        Instant runTwo = Instant.parse("2026-10-01T18:30:00Z"); // newer
        Instant runOne = Instant.parse("2026-10-01T09:15:00Z");

        DeliveryRow janNew = new DeliveryRow(11L, "Jan Kowalski", "jan@test.com",
                "Marsz Radwański", "Trąbka 1", 1, 3, "EMAIL", "DELIVERED", null,
                ACTOR_EMAIL, runTwo);
        DeliveryRow annaNew = new DeliveryRow(12L, "Anna Nowak", null,
                "Marsz Radwański", "Bęben", 4, 6, "EMAIL", "SKIPPED_NO_CONSENT", null,
                ACTOR_EMAIL, runTwo);
        DeliveryRow janOld = new DeliveryRow(3L, "Jan Kowalski", "jan@test.com",
                "Hejnał", "Trąbka 1", 5, 8, "EMAIL", "SEND_FAILED", "SMTP timeout", ACTOR_EMAIL, runOne);

        // runs list is NEWEST-first: runTwo comes before runOne.
        DeliveryHistory history = new DeliveryHistory(EVENT_ID,
                1,                                  // totalDeliveredParts — only the runTwo DELIVERED
                2,                                  // totalSkippedParts — Anna + Jan's failed send
                List.of(new DeliveryBlock(runTwo, List.of(janNew, annaNew)),
                        new DeliveryBlock(runOne, List.of(janOld))));

        when(partDeliveryHistoryService.historyForEvent(EVENT_ID, BAND_ID)).thenReturn(history);

        String body = history(freshSession())
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("a 200 carries the usable envelope, not '{}'. Body: %s", body)
                .doesNotContain("{}")
                .contains("\"eventId\"")
                .contains("\"totalDeliveredParts\":1")
                .contains("\"totalSkippedParts\":2");
        // Every honest outcome survives the wire — the UI renders these strings verbatim.
        assertThat(body).contains("Jan Kowalski").contains("Anna Nowak")
                .contains("SKIPPED_NO_CONSENT").contains("SEND_FAILED").contains("SMTP timeout");
        // Newest run first — the panel's "ostatnia próba" must be runTwo. runAt serializes as ISO
        // (the block has no @JsonFormat), so compare the ISO instant strings.
        assertThat(body.indexOf("\"runAt\":\"" + runTwo.toString() + "\""))
                .as("newest run appears before the older one in the JSON")
                .isLessThan(body.indexOf("\"runAt\":\"" + runOne.toString() + "\""));
    }

    @Test
    @DisplayName("US-6.6 history: an event with no delivery yet → 200, zero counters, empty runs")
    void history_empty_returnsZeroNotAnError() throws Exception {
        when(partDeliveryHistoryService.historyForEvent(EVENT_ID, BAND_ID))
                .thenReturn(DeliveryHistory.empty(EVENT_ID));

        String body = history(freshSession())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("\"totalDeliveredParts\":0")
                .contains("\"totalSkippedParts\":0")
                .contains("\"runs\":[]");
    }

    // ─────────────────────── error contracts (404 / 409 / 400) ───────────────────────

    @Test
    @DisplayName("unknown event → 404, never the generic RuntimeException→500")
    void history_unknownEvent_notFound() throws Exception {
        doThrow(new EventNotFoundException(EVENT_ID))
                .when(partDeliveryHistoryService).historyForEvent(anyLong(), anyLong());

        String body = history(freshSession())
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("a readable JSON error body, not a Spring stack trace page. Body: %s", body)
                .contains("91");
    }

    @Test
    @DisplayName("foreign-band event → 409 (the band-isolation contract, same as the write path)")
    void history_crossBand_conflict() throws Exception {
        doThrow(new IllegalStateException("Wydarzenie " + EVENT_ID + " nie należy do zespołu " + BAND_ID))
                .when(partDeliveryHistoryService).historyForEvent(anyLong(), anyLong());

        history(freshSession()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("no active team (anonymous / non-OIDC principal) → 400 before the service is touched")
    void history_noActiveTeam_badRequest() throws Exception {
        principalRef.set(null); // resolveActiveTeamId must bail out — no WindbandOidcUser to read

        history(freshSession()).andExpect(status().isBadRequest());

        verify(partDeliveryHistoryService, never()).historyForEvent(anyLong(), anyLong());
    }
}
