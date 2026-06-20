package pl.michalbzowski.windband.application.command.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.InventoryRepository;
import pl.michalbzowski.windband.domain.inventory.UniformItem;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying that {@link InventoryCommandService} uses the
 * band passed as parameter rather than a hardcoded band id (1).
 *
 * <p>Scenario:
 * <ol>
 *   <li>Band 1 exists (seeded by Flyway).</li>
 *   <li>Band 2 is created for testing.</li>
 *   <li>{@code addUniformItem()} is called with band 2.</li>
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
    //  TEST: addUniformItem uses band parameter, not hardcoded band_id=1
    // ====================================================================

    @Test
    void addUniformItem_shouldUseBandParameter_notDefaultBand1() {
        // When: add uniform item with band2
        UniformItem result = commandService.addUniformItem(null, null, band2);

        // Then: the uniform must belong to band_id=2, NOT band_id=1
        assertThat(result.getBand().getId())
                .as("Uniform must be created with band_id=%d (from parameter), not band_id=1", savedBand2Id)
                .isEqualTo(savedBand2Id);

        // Verify via query by band
        List<UniformItem> band2Items = inventoryRepository.findAllUniformItemsByBandId(savedBand2Id);
        assertThat(band2Items)
                .as("Uniform must appear in band %d query", savedBand2Id)
                .hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void addUniformItem_withBand1_shouldCreateInBand1() {
        // When: add uniform item with band1
        UniformItem result = commandService.addUniformItem(null, null, band1);

        // Then: uniform must belong to band_id=1
        assertThat(result.getBand().getId())
                .as("Uniform must be created with band_id=1 when band1 is passed")
                .isEqualTo(1L);

        List<UniformItem> band1Items = inventoryRepository.findAllUniformItemsByBandId(1L);
        assertThat(band1Items)
                .as("Uniform must appear in band 1 query")
                .hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void addUniformItem_withNullBand_shouldThrowException() {
        // When/Then: should throw when band is null
        assertThatThrownBy(() -> commandService.addUniformItem(null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("band is null");
    }

    // ====================================================================
    //  TEST: addInstrumentItem uses band parameter
    // ====================================================================

    @Test
    void addInstrumentItem_shouldUseBandParameter_notDefaultBand1() {
        // When: add instrument item with band2
        var result = commandService.addInstrumentItem(null, null, band2);

        // Then: the instrument must belong to band_id=2
        assertThat(result.getBand().getId())
                .as("Instrument must be created with band_id=%d (from parameter)", savedBand2Id)
                .isEqualTo(savedBand2Id);
    }

    // ====================================================================
    //  TEST: cross-team isolation — item in band 2 must not appear in band 1 query
    // ====================================================================

    @Test
    void uniformInBand2_shouldNotBeVisibleInBand1() {
        // Given: create uniform in band 2
        UniformItem itemInBand2 = commandService.addUniformItem(null, null, band2);

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
