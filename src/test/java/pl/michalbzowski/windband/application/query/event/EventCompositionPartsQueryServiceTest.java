package pl.michalbzowski.windband.application.query.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.command.composition.CompositionCommandService;
import pl.michalbzowski.windband.application.command.composition.CreateCompositionCommand;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.Distribution;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PartAssignment;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PartView;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.composition.PartSource;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-6.2 — the event × member part-list read model, tested against a real database.
 *
 * <p>Contract pinned here:
 * <ol>
 *   <li>happy path — two setlist pieces keep their musical order, each active member gets exactly
 *       their role's page range;</li>
 *   <li>US-5.1 consumption — a player tagged "Kornet" (alias of "Trąbka") covers the whole alias
 *       family's roles ("Trąbka 2", "Kornet 1"), and the matched tag is recorded on the row;</li>
 *   <li>exact-name match WITHOUT any role-map row — free-form US-7.1 roles still route to players;</li>
 *   <li>a part covered through SEVERAL of a member's tags is listed ONLY ONCE for that member;</li>
 *   <li>band isolation — foreign band → IllegalStateException (409) and no other tenant's data;
 *       unknown band id → IllegalArgumentException (400); null team → empty, fail-closed list;</li>
 *   <li>setlist pieces with no parts yet surface in {@code unassigned()} instead of vanishing;</li>
 *   <li>only ACTIVE members are listed;</li>
 *   <li>{@code fileRef} and the honest {@code verified} flag survive into the DTO.</li>
 * </ol>
 *
 * <p>Fixtures use a fresh band + UUID suffix per test (mirroring
 * {@code InstrumentRoleResolutionIntegrationTest}) so the V36 band-1 seed in {@code data.sql}
 * never collides with the unique indexes on compositions / role map rows.
 */
@Transactional
class EventCompositionPartsQueryServiceTest extends BaseIntegrationTest {

    @Autowired private BandRepository bandRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private InstrumentRoleMapRepository roleMapRepository;
    @Autowired private CompositionCommandService compositionCommandService;
    @Autowired private CompositionInstrumentRepository partRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventCompositionRepository eventCompositionRepository;
    @Autowired private EventCompositionPartsQueryService service;

    private Band bandA;
    /** Foreign tenant — cross-band fixtures live here. */
    private Band bandB;
    private BandEvent eventA;
    private BandEvent eventB;

    /** "Marsz" (2 parts) + "Walc" (0 parts) on band A's event, setlist order 1 then 2. */
    private Composition marsza;
    /** No parts yet — the US-3.03 gate not passed. */
    private Composition walec;

    @BeforeEach
    void seedTenants() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        bandA = bandRepository.save(Band.create("US62 A-" + suffix, "us62a-" + suffix));
        bandB = bandRepository.save(Band.create("US62 B-" + suffix, "us62b-" + suffix));

        String marszTitle = "Marsz US-6.2 " + suffix;
        String walecTitle = "Walc US-6.2 " + suffix;

        // Band A — the rich tenant: aliases, role map rows, three members (one inactive), 2-piece setlist.
        trumbkaA = instrumentRepository.save(Instrument.create("Trąbka", bandA));
        kornetA = instrumentRepository.save(Instrument.create("Kornet", bandA));
        bubenA = instrumentRepository.save(Instrument.create("Bęben", bandA));
        kornetA.setAliasOf(trumbkaA);
        instrumentRepository.save(kornetA);

        roleMap(bandA, "Trąbka", "Trąbka 2");
        roleMap(bandA, "Kornet", "Kornet 1"); // a second role of the family — must stay unrouted on Marsz

        jan = newMemberIn(bandA, "Jan", kornetA);   // alias-family player → covers Trąbka* + Kornet 1
        anna = newMemberIn(bandA, "Anna", bubenA);  // exact-name player → Bęben
        zosia = newMemberIn(bandA, "Zosia", bubenA);
        zosia.update("Zosia", "Rezygnatka", null, false); // resigned → must never appear

        this.marszTitleValue = marszTitle;
        this.walecTitleValue = walecTitle;
        marsza = composition(bandA.getId(), marszTitle);
        addPart(marsza, trumbkaA, "Trąbka 2", 1, 3);
        addPart(marsza, bubenA, "Bęben", 4, 6);
        walec = composition(bandA.getId(), walecTitle); // no parts → must appear in unassigned()

        eventA = newEvent(bandA, "Koncert A " + suffix);
        link(eventA, marsza, 1);
        link(eventA, walec, 2);

        // Band B — the foreign tenant: same shapes, different owner. Must never leak into band A's reads.
        trumbkaB = instrumentRepository.save(Instrument.create("Trąbka", bandB));
        kornetB = instrumentRepository.save(Instrument.create("Kornet", bandB));
        roleMap(bandB, "Kornet", "Kornet 1");
        felix = newMemberIn(bandB, "Felix", kornetB);
        foreignComposition = composition(bandB.getId(), "Marsz Cudz " + suffix);
        addPart(foreignComposition, kornetB, "Kornet 1", 1, 2);
        eventB = newEvent(bandB, "Koncert B " + suffix);
        link(eventB, foreignComposition, 1);
    }

    private String marszTitleValue;
    private String walecTitleValue;
    private Instrument trumbkaA;
    private Instrument kornetA;
    private Instrument bubenA;
    private Instrument trumbkaB;
    private Instrument kornetB;
    private Member jan;
    private Member anna;
    private Member zosia;
    private Member felix; // band B — must never leak into band A's distribution
    private Composition foreignComposition;

    // ─────────────────────────────── happy path ───────────────────────────────

    @Test
    void happyPath_setlistOrderRolesPageRanges() {
        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        assertThat(d.assignments()).hasSize(2);

        PartAssignment brass = rowByRole(d, "Trąbka 2");
        assertThat(brass.compositionTitle()).isEqualTo(marszTitleValue);
        assertThat(brass.setlistPosition()).isEqualTo(1);
        assertThat(brass.pageFrom()).as("page range printed verbatim").isEqualTo(1);
        assertThat(brass.pageTo()).isEqualTo(3);
        assertThat(brass.matchedMemberTag()).isEqualTo("Kornet"); // Jan plays the alias "Kornet"
        assertThat(brass.emailConsentGiven()).isFalse();

        PartAssignment drums = rowByRole(d, "Bęben");
        assertThat(drums.compositionTitle()).isEqualTo(marszTitleValue);
        assertThat(drums.pageFrom()).isEqualTo(4);
        assertThat(drums.pageTo()).isEqualTo(6);
        assertThat(drums.matchedMemberTag()).isEqualTo("Bęben");

        // No foreign-tenant name may ever surface in band A's distribution.
        assertThat(memberNames(d)).doesNotContain("Felix Testowy");
    }

    @Test
    void pieceWithoutParts_isSurfacedUnassignedWithItsSetlistPosition() {
        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        assertThat(d.unassigned()).hasSize(1);
        assertThat(d.unassigned().get(0).title()).isEqualTo(walecTitleValue);
        assertThat(d.unassigned().get(0).compositionId()).isEqualTo(walec.getId());
        assertThat(d.unassigned().get(0).setlistPosition()).isEqualTo(2);
    }

    // ─────────────────────── matching semantics (US-5.3) ───────────────────────

    @Test
    void aliasPlayer_coversTheWholeFamilyAndOnlyClaimsMappedRoles() {
        // Jan plays "Kornet"; the family resolves to {Trąbka 2, Kornet 1}. On Marsz only "Trąbka 2"
        // is a part → exactly ONE row for him on that role, tagged with his own instrument.
        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        List<PartAssignment> janRows = d.assignments().stream()
                .filter(a -> a.memberId() == jan.getId())
                .toList();
        assertThat(janRows).hasSize(1); // "Kornet 1" is NOT on Marsz — no phantom row
        assertThat(janRows.get(0).role()).isEqualTo("Trąbka 2");
        assertThat(janRows.get(0).matchedMemberTag()).isEqualTo("Kornet");
    }

    @Test
    void exactNameMatch_worksWithoutAnyRoleMapRow() {
        // "Bęben" has NO instrument_role_map row in band A — pure tag==role match must still route,
        // and it should list the player without consulting the resolver at all.
        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        assertThat(rowByRole(d, "Bęben").memberId()).isEqualTo(anna.getId());
    }

    @Test
    void partCoveredViaTwoTags_listsTheMemberOnlyOnce() {
        // Give Anna BOTH tags of the Trąbka alias family — then "Trąbka 2" is covered through two
        // of her tags (root + alias, via their own role-map rows). She must still get exactly ONE row.
        anna.addInstrument(kornetA, false);
        anna.addInstrument(trumbkaA, false);

        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        assertThat(d.assignments())
                .filteredOn(a -> a.memberId() == anna.getId() && a.role().equals("Trąbka 2"))
                .as("one part covered by two tags → one row")
                .hasSize(1);
    }

    // ─────────────── piece view (main) and uncovered parts ─────────────

    @Test
    void piecesView_isGroupedInSetlistOrder_playersResolvedUncoveredSurfaced() {
        // Marsz gets a part that no one in band A can play -> it must surface as "no player", not vanish.
        Instrument fletA = instrumentRepository.save(Instrument.create("Flet", bandA));
        addPart(marsza, fletA, "Flet 1", 7, 9);

        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        // Two pieces in set-list order.
        assertThat(d.pieces()).hasSize(2);
        assertThat(d.pieces().get(0).title()).isEqualTo(marszTitleValue);
        assertThat(d.pieces().get(0).setlistPosition()).isEqualTo(1);
        assertThat(d.pieces().get(1).title()).isEqualTo(walecTitleValue);
        assertThat(d.pieces().get(1).hasNoParts()).isTrue();

        // Marsz has 3 parts, exactly one uncovered (Flet 1).
        var marsz = d.pieces().get(0);
        assertThat(marsz.parts()).hasSize(3);
        assertThat(marsz.uncoveredParts()).extracting(PartView::role).containsExactly("Flet 1");

        var flet = marsz.parts().stream().filter(pt -> pt.role().equals("Flet 1")).findFirst().orElseThrow();
        assertThat(flet.players()).isEmpty(); // the gap is explicit, pages + file ref kept
        assertThat(flet.pageFrom()).isEqualTo(7);
        assertThat(flet.pageTo()).isEqualTo(9);

        var brass = marsz.parts().stream().filter(pt -> pt.role().equals("Trąbka 2")).findFirst().orElseThrow();
        assertThat(brass.players()).singleElement().satisfies(pl -> {
            assertThat(pl.memberId()).isEqualTo(jan.getId());
            assertThat(pl.memberName()).contains("Jan");
            assertThat(pl.emailConsentGiven()).isFalse(); // consent handed to the US-6.3 mailer gate
        });
    }

    @Test
    void uncoveredPart_doesNotCreateFlatRows() {
        Instrument fletA = instrumentRepository.save(Instrument.create("Flet", bandA));
        addPart(marsza, fletA, "Flet 1", 7, 9);

        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        assertThat(d.assignments())
                .as("a part nobody plays results in no flat row")
                .noneMatch(a -> a.role().equals("Flet 1"));
    }

    // ──────────────────────────── population filter ────────────────────────────

    @Test
    void inactiveMembers_areNeverListed() {
        Distribution d = service.forEvent(eventA.getId(), bandA.getId());

        assertThat(memberNames(d)).doesNotContain("Zosia Rezygnatka");
        assertThat(d.assignments())
                .as("a resigned member's id must never be routed a part")
                .extracting(PartAssignment::memberId)
                .doesNotContain(zosia.getId());
    }

    // ───────────────────────────── band isolation ─────────────────────────────

    @Test
    void foreignBandRead_isRefusedAndLeaksNothing() {
        Distribution own = service.forEvent(eventA.getId(), bandA.getId());
        List<String> names = memberNames(own);

        assertThat(names).contains("Jan Testowy");
        assertThatThrownBy(() -> service.forEvent(eventA.getId(), bandB.getId()))
                .isInstanceOf(IllegalStateException.class) // → HTTP 409, not a silent empty page
                .hasMessageContaining("nie należy do zespołu");

        // Symmetrically: band A may not read band B's event.
        assertThatThrownBy(() -> service.forEvent(eventB.getId(), bandA.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie należy do zespołu");
    }

    @Test
    void unknownBandId_failsClosedWithBadRequest() {
        long ghost = 987_654_321L;
        assertThatThrownBy(() -> service.forEvent(eventA.getId(), ghost))
                .isInstanceOf(IllegalArgumentException.class) // → HTTP 400
                .hasMessageContaining(String.valueOf(ghost));
    }

    @Test
    void nullTeamContext_degradesToEmptyDistribution() {
        Distribution d = service.forEvent(eventA.getId(), (Long) null);

        assertThat(d.assignments()).as("no team context → nothing is listed").isEmpty();
        // The piece list itself is readable metadata and must still be surfaced, not hidden.
        assertThat(d.unassigned()).isNotEmpty();
    }

    @Test
    void emptySetlist_eventStillResolvesToEmptyListsWithoutParts() {
        BandEvent lonely = newEvent(bandA, "Koncert bez setlisty");
        Distribution d = service.forEvent(lonely.getId(), bandA.getId());

        assertThat(d.assignments()).isEmpty();
        assertThat(d.unassigned()).isEmpty();
    }

    // ───────────────────────── DTO field fidelity (US-6.3 hand-off) ─────────────────────────

    @Test
    void fileRef_andVerifiedFlag_surviveIntoTheDto() {
        // A ZIP-mode part (fileRef set) must be carried verbatim, and the verified flag must be
        // honest: false before a librarian confirms, true afterwards, audit included.
        Composition suite = composition(bandA.getId(), "Suite US-6.2");
        CompositionInstrument zipPart = addPartWithFile(suite, kornetA, "Trąbka 2", 10, 12, "04_trąbka_2.png");
        BandEvent zipEvent = newEvent(bandA, "Koncert ZIP");
        link(zipEvent, suite, 1);

        Distribution before = service.forEvent(zipEvent.getId(), bandA.getId());
        PartAssignment row = before.assignments().get(0);
        assertThat(row.fileRef()).isEqualTo("04_trąbka_2.png");
        assertThat(row.verified()).as("must be honest — not yet verified").isFalse();
        assertThat(row.pageFrom()).isEqualTo(10);
        assertThat(row.pageTo()).isEqualTo(12);

        zipPart.verify("librarian@test", Instant.now());

        Distribution after = service.forEvent(zipEvent.getId(), bandA.getId());
        assertThat(after.assignments().get(0).verified()).isTrue();
    }

    // ─────────────── US-6.5 — invalidation hook visible through the Epic 6 read model ─────────────

    /**
     * US-6.5 — "Regenerate on fly when a composer edits part ranges": the distribution read model is
     * live and must not cache verification state. The sequence below mirrors the US-6.3 hand-off:
     * once a librarian confirms (US-6.2 → verified=true), a composer edits that part's page range
     * (US-7.1 / issue #241 path, {@code CompositionCommandService.updatePart}) — the frozen audit
     * pair that the "verified" flag is read from is invalidated by the US-6.5 domain hook in
     * {@code CompositionInstrument.updateMapping}, and the composition's own READY state drops back
     * to DRAFT (because the "whole-map approved" invariant no longer holds). The very next
     * {@link EventCompositionPartsQueryService#forEvent} call must already reflect verified=false,
     * proving there is NO in-flight cache between the write and this read. A fresh US-3.03 pass then
     * restores both: the flag back to true AND the composition to READY — closing the loop without a
     * second verification regime.
     */
    @Test
    void editPartAfterVerify_turnsVerifiedFlagsOffLive_untilReverified() {
        Composition suite = composition(bandA.getId(), "US-6.5 revalidation on edit");
        CompositionInstrument part = addPart(suite, kornetA, "Trąbka 2", 10, 12);
        BandEvent e = newEvent(bandA, "Koncert US-6.5");
        link(e, suite, 1);

        // ① Verified: a librarian stamps the (only) part → Epic 6's read model reports verified=true.
        compositionCommandService.verifyCompositionParts(suite.getId(), bandA.getId(), "lib@test");
        Distribution confirmed = service.forEvent(e.getId(), bandA.getId());
        assertThat(confirmed.assignments().get(0).verified()).as("confirmed before edit").isTrue();

        // ② A composer edits the part's page range (US-7.1 path) AFTER the fact — US-6.5 requires this
        //    to invalidate the frozen pair, so the *very next* distribution read must show false, with
        //    no cache in between. This is the entire, observable, non-cache guarantee of the story.
        compositionCommandService.updatePart(part.getId(), suite.getId(), kornetA.getId(),
                "Trąbka 2", 14, 19, null, bandA.getId());

        Distribution afterEdit = service.forEvent(e.getId(), bandA.getId());
        assertThat(afterEdit.assignments().get(0).verified())
                .as("edit must invalidate the 'verified' flag on the distribution read")
                .isFalse();

        // ③ Loop closes through the same US-3.03 gate, not a second regime: re-verify restores the flag.
        compositionCommandService.verifyCompositionParts(suite.getId(), bandA.getId(), "lib2@test");
        Distribution reconfirmed = service.forEvent(e.getId(), bandA.getId());
        assertThat(reconfirmed.assignments().get(0).verified()).as("restored after re-verification").isTrue();
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private static PartAssignment rowByRole(Distribution d, String role) {
        return d.assignments().stream()
                .filter(a -> a.role().equals(role))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no assignment for role " + role));
    }

    private static List<String> memberNames(Distribution d) {
        return d.assignments().stream().map(PartAssignment::memberName).toList();
    }

    private BandEvent newEvent(Band band, String name) {
        BandEvent event = BandEvent.create(name, LocalDate.now().plusDays(30), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT, band, PaymentType.FREE, null);
        return eventRepository.save(event);
    }

    private void link(BandEvent event, Composition composition, int orderInSet) {
        eventCompositionRepository.save(EventComposition.link(event, composition, orderInSet));
    }

    private InstrumentRoleMap roleMap(Band band, String sourceTag, String targetRole) {
        return roleMapRepository.save(InstrumentRoleMap.forBand(band, sourceTag, targetRole, null));
    }

    private Member newMemberIn(Band band, String firstName, Instrument instrument) {
        Member member = Member.create(firstName, "Testowy", null, band);
        member.addInstrument(instrument, true);
        return memberRepository.save(member);
    }

    private Composition composition(Long bandId, String title) {
        CreateCompositionCommand cmd = new CreateCompositionCommand();
        cmd.setTitle(title);
        cmd.setDescription("");
        cmd.setComposer("Test Composer");
        cmd.setArranger("Test Arranger");
        return compositionCommandService.create(cmd, bandId);
    }

    private CompositionInstrument addPart(Composition composition, Instrument instrument, String role,
                                          int pageFrom, int pageTo) {
        return partRepository.save(CompositionInstrument.forComposition(
                composition, instrument, role, pageFrom, pageTo, null, PartSource.MANUAL, 1.0));
    }

    private CompositionInstrument addPartWithFile(Composition composition, Instrument instrument, String role,
                                                  int pageFrom, int pageTo, String fileRef) {
        return partRepository.save(CompositionInstrument.forComposition(
                composition, instrument, role, pageFrom, pageTo, fileRef, PartSource.MANUAL, 1.0));
    }
}
