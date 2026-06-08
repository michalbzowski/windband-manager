package pl.michalbzowski.windband.application.command.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryCommandServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private BandRepository bandRepository;
    @Mock private UniformAttributeCommandService uniformAttributeCommandService;
    @Mock private InstrumentAttributeCommandService instrumentAttributeCommandService;
    @Mock private AwardAttributeCommandService awardAttributeCommandService;
    @Mock private AwardItemRepository awardItemRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private Authentication authentication;
    @Mock private SecurityContext securityContext;

    @InjectMocks
    private InventoryCommandService service;

    private Band band;
    private Member member;
    private UniformItem uniformItem;
    private InstrumentItem instrumentItem;
    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        band = Band.create("Test Band", "TB");
        setField(band, "id", 1L);

        member = Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 15), band);
        setField(member, "id", 1L);

        uniformItem = UniformItem.createOwned("Czapka", band);
        setField(uniformItem, "id", 1L);

        instrumentItem = InstrumentItem.createOwned("Trąbka", band);
        setField(instrumentItem, "id", 1L);

        currentUser = AppUser.create("admin", "admin@test.pl", "hash");
        setField(currentUser, "id", 1L);
    }

    @Test
    void shouldAssignUniformWithConditionAndCurrentUser() {
        when(inventoryRepository.findUniformItemById(1L)).thenReturn(Optional.of(uniformItem));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(inventoryRepository.findHistoryByUniformItem(uniformItem)).thenReturn(List.of());
        when(inventoryRepository.saveUniformItem(any())).thenReturn(uniformItem);
        when(inventoryRepository.saveAssignment(any())).thenReturn(null);

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));

        service.assignUniformToMember(1L, 1L, "nowy, dobry stan");

        ArgumentCaptor<AssetAssignmentHistory> captor = ArgumentCaptor.forClass(AssetAssignmentHistory.class);
        verify(inventoryRepository).saveAssignment(captor.capture());

        AssetAssignmentHistory saved = captor.getValue();
        assertThat(saved.getConditionAtAssign()).isEqualTo("nowy, dobry stan");
        assertThat(saved.getAssignedBy()).isEqualTo(currentUser);
        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getReturnedAt()).isNull();

        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAssignInstrumentWithConditionAndCurrentUser() {
        when(inventoryRepository.findInstrumentItemById(1L)).thenReturn(Optional.of(instrumentItem));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(inventoryRepository.findHistoryByInstrumentItem(instrumentItem)).thenReturn(List.of());
        when(inventoryRepository.saveInstrumentItem(any())).thenReturn(instrumentItem);
        when(inventoryRepository.saveAssignment(any())).thenReturn(null);

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));

        service.assignInstrumentToMember(1L, 1L, "używany, dobry");

        ArgumentCaptor<AssetAssignmentHistory> captor = ArgumentCaptor.forClass(AssetAssignmentHistory.class);
        verify(inventoryRepository).saveAssignment(captor.capture());

        AssetAssignmentHistory saved = captor.getValue();
        assertThat(saved.getConditionAtAssign()).isEqualTo("używany, dobry");
        assertThat(saved.getAssignedBy()).isEqualTo(currentUser);
        assertThat(saved.isActive()).isTrue();

        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnUniformWithCondition() {
        AssetAssignmentHistory activeHistory = AssetAssignmentHistory.forUniform(
                uniformItem, member, currentUser, "nowy", null);
        setField(activeHistory, "id", 1L);

        when(inventoryRepository.findUniformItemById(1L)).thenReturn(Optional.of(uniformItem));
        when(inventoryRepository.findHistoryByUniformItem(uniformItem)).thenReturn(List.of(activeHistory));
        when(inventoryRepository.saveUniformItem(any())).thenReturn(uniformItem);
        when(inventoryRepository.saveAssignment(any())).thenReturn(null);

        service.returnUniform(1L, "lekko zużyty", "Zwrócono po sezonie");

        ArgumentCaptor<AssetAssignmentHistory> captor = ArgumentCaptor.forClass(AssetAssignmentHistory.class);
        verify(inventoryRepository).saveAssignment(captor.capture());

        AssetAssignmentHistory updated = captor.getValue();
        assertThat(updated.getConditionAtReturn()).isEqualTo("lekko zużyty");
        assertThat(updated.getNotes()).isEqualTo("Zwrócono po sezonie");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getReturnedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    void shouldReturnInstrumentWithCondition() {
        AssetAssignmentHistory activeHistory = AssetAssignmentHistory.forInstrument(
                instrumentItem, member, currentUser, "dobry", null);
        setField(activeHistory, "id", 1L);

        when(inventoryRepository.findInstrumentItemById(1L)).thenReturn(Optional.of(instrumentItem));
        when(inventoryRepository.findHistoryByInstrumentItem(instrumentItem)).thenReturn(List.of(activeHistory));
        when(inventoryRepository.saveInstrumentItem(any())).thenReturn(instrumentItem);
        when(inventoryRepository.saveAssignment(any())).thenReturn(null);

        service.returnInstrument(1L, "wymaga regulacji", null);

        ArgumentCaptor<AssetAssignmentHistory> captor = ArgumentCaptor.forClass(AssetAssignmentHistory.class);
        verify(inventoryRepository).saveAssignment(captor.capture());

        AssetAssignmentHistory updated = captor.getValue();
        assertThat(updated.getConditionAtReturn()).isEqualTo("wymaga regulacji");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void shouldCloseActiveAssignmentOnReassign() {
        AssetAssignmentHistory oldHistory = AssetAssignmentHistory.forUniform(
                uniformItem, member, currentUser, "dobry", null);
        setField(oldHistory, "id", 1L);

        Member newMember = Member.create("Piotr", "Nowak", LocalDate.of(1985, 6, 20), band);
        setField(newMember, "id", 2L);

        when(inventoryRepository.findUniformItemById(1L)).thenReturn(Optional.of(uniformItem));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(newMember));
        when(inventoryRepository.findHistoryByUniformItem(uniformItem)).thenReturn(List.of(oldHistory));
        when(inventoryRepository.saveUniformItem(any())).thenReturn(uniformItem);
        when(inventoryRepository.saveAssignment(any())).thenReturn(null);

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));

        service.assignUniformToMember(1L, 2L, "dobry");

        verify(inventoryRepository, times(2)).saveAssignment(any());
        assertThat(oldHistory.isActive()).isFalse();
        assertThat(oldHistory.getReturnedAt()).isEqualTo(LocalDate.now());

        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldHandleNullConditionAtAssign() {
        when(inventoryRepository.findUniformItemById(1L)).thenReturn(Optional.of(uniformItem));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(inventoryRepository.findHistoryByUniformItem(uniformItem)).thenReturn(List.of());
        when(inventoryRepository.saveUniformItem(any())).thenReturn(uniformItem);
        when(inventoryRepository.saveAssignment(any())).thenReturn(null);

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");
        when(appUserRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));

        service.assignUniformToMember(1L, 1L, null);

        ArgumentCaptor<AssetAssignmentHistory> captor = ArgumentCaptor.forClass(AssetAssignmentHistory.class);
        verify(inventoryRepository).saveAssignment(captor.capture());
        assertThat(captor.getValue().getConditionAtAssign()).isNull();

        SecurityContextHolder.clearContext();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
}
