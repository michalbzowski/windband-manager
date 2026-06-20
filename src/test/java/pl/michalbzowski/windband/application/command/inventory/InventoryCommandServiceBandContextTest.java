package pl.michalbzowski.windband.application.command.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.InventoryRepository;
import pl.michalbzowski.windband.domain.inventory.UniformItem;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying that {@link InventoryCommandService} uses the
 * band from the security context ({@link WindbandOidcUser#getActiveTeamId()})
 * rather than a hardcoded band id (1).
 *
 * <p>Scenario:
 * <ol>
 *   <li>Band 1 exists (seeded by Flyway).</li>
 *   <li>Band 2 is created for testing.</li>
 *   <li>A {@link WindbandOidcUser} with {@code activeTeamId=2} is placed in the security context.</li>
 *   <li>{@code addUniformItem()} is called.</li>
 *   <li>The saved uniform must have {@code band_id=2}, NOT band_id=1.</li>
 * </ol>
 *
 * <p>This catches the bug where the service falls back to band_id=1 regardless
 * of the logged-in user's active team.
 */
@Transactional
class InventoryCommandServiceBandContextTest extends BaseIntegrationTest {

    @Autowired
    private InventoryCommandService commandService;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Band band1;
    private Band band2;
    private Long savedBand2Id;

    @BeforeEach
    void setUp() {
        // Clean up any test data from previous runs (Testcontainers shared across classes)
        cleanupTestData();

        // Band 1 exists from Flyway seed (id=1, "MOD Strażak")
        band1 = bandRepository.findById(1L).orElseThrow();

        // Create Band 2 for multi-team testing
        Band newBand = Band.create("Drugi Zespół", "drugi-zespol");
        band2 = bandRepository.save(newBand);
        savedBand2Id = band2.getId();
        assertThat(savedBand2Id).isNotEqualTo(1L);
    }

    // ====================================================================
    //  TEST: addUniformItem uses band from security context, not band_id=1
    // ====================================================================

    @Test
    void addUniformItem_shouldUseBandFromSecurityContext_notDefaultBand1() {
        // Given: security context with activeTeamId=2
        setSecurityContextWithActiveTeam(savedBand2Id, "test-band-2");

        // When: add uniform item (no member, no attributes — pure band test)
        UniformItem result = commandService.addUniformItem(null, null);

        // Then: the uniform must belong to band_id=2, NOT band_id=1
        assertThat(result.getBand().getId())
                .as("Uniform must be created with band_id=%d (from security context), not band_id=1", savedBand2Id)
                .isEqualTo(savedBand2Id);

        // Verify via query by band
        List<UniformItem> band1Items = inventoryRepository.findAllUniformItemsByBandId(1L);
        List<UniformItem> band2Items = inventoryRepository.findAllUniformItemsByBandId(savedBand2Id);

        assertThat(band2Items)
                .as("Uniform must appear in band %d query", savedBand2Id)
                .hasSizeGreaterThanOrEqualTo(1);
        assertThat(band1Items)
                .as("Uniform must NOT appear in band 1 query")
                .noneMatch(u -> result.getId() != null && band1Items.stream().anyMatch(b1 -> b1.getId().equals(result.getId())));
    }

    @Test
    void addUniformItem_withBand1Context_shouldCreateInBand1() {
        // Given: security context with activeTeamId=1
        setSecurityContextWithActiveTeam(1L, "test-band");

        // When: add uniform item
        UniformItem result = commandService.addUniformItem(null, null);

        // Then: uniform must belong to band_id=1
        assertThat(result.getBand().getId())
                .as("Uniform must be created with band_id=1 when activeTeamId=1")
                .isEqualTo(1L);

        List<UniformItem> band1Items = inventoryRepository.findAllUniformItemsByBandId(1L);
        assertThat(band1Items)
                .as("Uniform must appear in band 1 query")
                .hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void addUniformItem_withoutWindbandOidcUser_shouldThrowException() {
        // Given: security context WITHOUT WindbandOidcUser (plain auth)
        Authentication auth = new org.springframework.security.authentication.
                UsernamePasswordAuthenticationToken("admin", "admin",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // When/Then: should throw because getActiveBand() returns null
        assertThatThrownBy(() -> commandService.addUniformItem(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no active team");

        SecurityContextHolder.clearContext();
    }

    @Test
    void addUniformItem_withNullTeamId_shouldThrowException() {
        // Given: security context with WindbandOidcUser but null activeTeamId
        setSecurityContextWithActiveTeam(null, null);

        // When/Then: should throw because getActiveBand() returns null
        assertThatThrownBy(() -> commandService.addUniformItem(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no active team");

        SecurityContextHolder.clearContext();
    }

    // ====================================================================
    //  TEST: addInstrumentItem uses band from security context
    // ====================================================================

    @Test
    void addInstrumentItem_shouldUseBandFromSecurityContext_notDefaultBand1() {
        // Given: security context with activeTeamId=2
        setSecurityContextWithActiveTeam(savedBand2Id, "test-band-2");

        // When: add instrument item
        var result = commandService.addInstrumentItem(null, null);

        // Then: the instrument must belong to band_id=2
        assertThat(result.getBand().getId())
                .as("Instrument must be created with band_id=%d (from security context)", savedBand2Id)
                .isEqualTo(savedBand2Id);

        SecurityContextHolder.clearContext();
    }

    // ====================================================================
    //  TEST: cross-team isolation — item in band 2 must not appear in band 1 query
    // ====================================================================

    @Test
    void uniformInBand2_shouldNotBeVisibleInBand1() {
        // Given: create uniform in band 2
        setSecurityContextWithActiveTeam(savedBand2Id, "test-band-2");
        UniformItem itemInBand2 = commandService.addUniformItem(null, null);
        SecurityContextHolder.clearContext();

        // When: query band 1 items
        List<UniformItem> band1Items = inventoryRepository.findAllUniformItemsByBandId(1L);

        // Then: item from band 2 must NOT appear in band 1
        assertThat(band1Items)
                .as("Uniform created in band %d must NOT appear in band 1 query", savedBand2Id)
                .noneMatch(u -> u.getId().equals(itemInBand2.getId()));
    }

    // ====================================================================
    //  Helpers
    // ====================================================================

    private void setSecurityContextWithActiveTeam(Long activeTeamId, String activeTeamSlug) {
        // Minimal OidcIdToken
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "test-subject");
        claims.put("preferred_username", "testuser");
        claims.put("email", "testuser@test.com");
        claims.put("name", "Test User");
        OidcIdToken idToken = new OidcIdToken(
                "mock-token", Instant.now(), Instant.now().plusSeconds(3600), claims);

        DefaultOidcUser delegate = new DefaultOidcUser(
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                idToken);

        WindbandOidcUser wu = new WindbandOidcUser(
                delegate,
                100L,              // userId
                "testuser",        // username
                "testuser@test.com", // email
                true,              // active
                activeTeamId,      // activeTeamId — the key field!
                activeTeamSlug,    // activeTeamSlug
                "ADMIN",           // activeTeamRole
                activeTeamId != null ? List.of(activeTeamId) : List.of()
        );

        Authentication auth = new org.springframework.security.authentication.
                UsernamePasswordAuthenticationToken(wu, null, wu.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    private void cleanupTestData() {
        // Remove test instrument items (via inventory repo)
        inventoryRepository.findAllInstrumentItems().forEach(i -> {
            if (i.getId() != null && !i.getBand().getId().equals(1L)) {
                inventoryRepository.deleteInstrumentItem(i);
            }
        });

        // Remove test members
        memberRepository.findAllActive().stream()
                .filter(m -> m.getFirstName() != null && m.getFirstName().startsWith("E2E"))
                .forEach(memberRepository::delete);

        // Remove test bands (but not band 1 which is Flyway seed)
        bandRepository.findAll().stream()
                .filter(b -> !b.getId().equals(1L))
                .filter(b -> b.getName().contains("Drugi") || b.getName().contains("Test"))
                .forEach(bandRepository::delete);

        // Remove test uniform items (any non-band-1 items)
        inventoryRepository.findAllUniformItems().forEach(u -> {
            if (u.getId() != null && !u.getBand().getId().equals(1L)) {
                inventoryRepository.deleteUniformItem(u);
            }
        });
    }
}
