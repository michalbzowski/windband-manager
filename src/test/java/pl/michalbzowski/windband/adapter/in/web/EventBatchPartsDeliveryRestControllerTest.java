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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;

import pl.michalbzowski.windband.application.command.event.EventCommandService;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.application.command.event.EventPartDeliveryCommandService;
import pl.michalbzowski.windband.application.command.event.NotificationSender;
import pl.michalbzowski.windband.application.command.event.PartDeliveryResult;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-6.4 — contract test for {@code POST /api/events/{id}/send-all-parts}: the batch version of
 * US-6.3, delivering <em>every</em> routed part of an event's setlist (in concert order) to every
 * musician who plays at least one of them.
 *
 * <p>The delivery <em>policy</em> itself (consent gate, honest buckets, token links) is already
 * pinned by {@code EventPartDeliveryCommandServiceTest} and its integration test; this class pins
 * only what a REST endpoint adds on top: a stable HTTP contract —
 * <ul>
 *   <li>200 + an <b>honest JSON breakdown</b> (not an empty {@code {}}), so the header button can
 *       read how many e-mails went out and who was skipped, without a stack trace in the payload;</li>
 *   <li>404 for an unknown event — consistent with every other endpoint in this API, which maps
 *       {@link EventNotFoundException} through {@link GlobalExceptionHandler};</li>
 *   <li>409 for a foreign-band / cross-tenant event (service's {@code IllegalStateException});</li>
 *   <li>400 when there is no active team context — before the service is ever touched.</li>
 * </ul>
 *
 * <p>Mirrors the standalone-MockMvc convention of the other REST tests in this package
 * ({@link ScoreFileDeleteRestControllerTest}): plain Mockito, {@link GlobalExceptionHandler}
 * attached as controller advice, no Spring context. The {@code @AuthenticationPrincipal OidcUser}
 * is injected through a deterministic argument resolver (the standalone setup does not wire in the
 * Spring Security one), so each test gets exactly the principal it declares.
 */
@ExtendWith(MockitoExtension.class)
class EventBatchPartsDeliveryRestControllerTest {

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
    private pl.michalbzowski.windband.application.query.event.EventPartDeliveryQueryService partDeliveryHistoryService;
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

        mvc = MockMvcBuilders.standaloneSetup(new EventController(commandService, queryService,
                        teamQueryService, notificationSender, partDeliveryService,
                        partDeliveryHistoryService, restTemplate))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver())
                .build();
    }

    /** No session attribute {@code activeTeamId} — exercises the user's own active team fallback. */
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

    private ResultActions batch(MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/events/" + EVENT_ID + "/send-all-parts").session(session));
    }

    // ─────────────────────────── 200 — honest breakdown ───────────────────────────

    @Test
    @DisplayName("US-6.4 success: full-setlist batch is reported as a complete, honest JSON breakdown")
    void sendAllParts_success_returnsSentAndBuckets() throws Exception {
        PartDeliveryResult.Delivered jan = new PartDeliveryResult.Delivered(101L, "Jan Kowalski",
                "jan@test.com", List.of(
                        // piece #1 in the setlist (position 1)
                        new PartDeliveryResult.PartDeliveryRow(1100L, "Marsz Radwański", "Trąbka 1",
                                1, 3, "http://localhost:8080/public/parts/" + UUID.randomUUID()),
                        // same member, piece #2 (position 2) — the batch covers the WHOLE setlist
                        new PartDeliveryResult.PartDeliveryRow(1201L, "Hejnał", "Trąbka 1",
                                4, 8, "http://localhost:8080/public/parts/" + UUID.randomUUID())));
        PartDeliveryResult.Delivered anna = new PartDeliveryResult.Delivered(102L, "Anna Nowak",
                "anna@test.com", List.of(
                        new PartDeliveryResult.PartDeliveryRow(1101L, "Marsz Radwański", "Bęben",
                                5, 9, "http://localhost:8080/public/parts/" + UUID.randomUUID())));

        PartDeliveryResult result = PartDeliveryResult.of(
                2,                                    // sent — two e-mails left the app
                List.of(jan, anna),                   // delivered
                List.of(),                            // skippedNoConsent
                List.of("Piotr Zalewski"),            // skippedNoEmail — honest skip bucket
                List.of("Kasia Wilk — „Flet 1” (strony 7–9)"), // covering-file refused
                List.of(),                            // failedSend
                null);                                // no last transport error

        when(partDeliveryService.deliverParts(EVENT_ID, BAND_ID, ACTOR_EMAIL)).thenReturn(result);

        String body = batch(freshSession())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("a 200 must carry the honest breakdown — 'Wysłano 0' (an empty object) would " +
                        "make a fully successful batch look like zero deliveries. Body: %s", body)
                .doesNotContain("{}");

        // Every honest bucket the UI banner reads — pinned by name so a future rename breaks loudly.
        assertThat(body).contains("\"sent\"").contains("Jan Kowalski").contains("Anna Nowak")
                .contains("Piotr Zalewski").contains("Kasia Wilk")
                .as("the breakdown lists who got the parts, who was skipped and what was refused");
    }

    @Test
    @DisplayName("US-6.4 empty batch (no assignments) → 200 sent=0, nothing else")
    void sendAllParts_emptyBatch_isZeroNotAnError() throws Exception {
        PartDeliveryResult result = PartDeliveryResult.of(0, List.of(), List.of(), List.of(),
                List.of(), List.of(), null);
        when(partDeliveryService.deliverParts(EVENT_ID, BAND_ID, ACTOR_EMAIL)).thenReturn(result);

        String body = batch(freshSession())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"sent\"")
                .as("the UI reads $.sent to render 'Wysłano N' — it must always be present");
    }

    // ─────────────────────── error contracts (404 / 409 / 400) ───────────────────────

    @Test
    @DisplayName("unknown event → 404, never the generic RuntimeException→500")
    void sendAllParts_unknownEvent_notFound() throws Exception {
        doThrow(new EventNotFoundException(EVENT_ID))
                .when(partDeliveryService).deliverParts(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());

        String body = batch(freshSession())
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("a readable JSON error body, not a Spring stack trace page. Body: %s", body)
                .contains("91");
    }

    @Test
    @DisplayName("foreign-band event → 409 (the band-isolation contract, same as the web form path)")
    void sendAllParts_crossBand_conflict() throws Exception {
        doThrow(new IllegalStateException("Wydarzenie " + EVENT_ID + " nie należy do zespołu " + BAND_ID))
                .when(partDeliveryService).deliverParts(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());

        batch(freshSession()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("no active team (anonymous / non-OIDC principal) → 400 before the service is touched")
    void sendAllParts_noActiveTeam_badRequest() throws Exception {
        principalRef.set(null); // resolveActiveTeamId must bail out — no WindbandOidcUser to read

        batch(freshSession()).andExpect(status().isBadRequest());

        verify(partDeliveryService, never())
                .deliverParts(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
