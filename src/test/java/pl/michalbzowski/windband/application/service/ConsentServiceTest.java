package pl.michalbzowski.windband.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.ConsentToken;
import pl.michalbzowski.windband.domain.member.ConsentTokenRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private ConsentTokenRepository tokenRepository;

    @InjectMocks
    private ConsentService consentService;

    private Member member;
    private ConsentType type;
    private UUID token;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        type = ConsentType.EVENTS;
        token = UUID.randomUUID();
    }

    @Test
    void shouldGrantConsentWhenNotExisting() {
        // given - token exists, but no consent yet
        ConsentToken mockToken = mock(ConsentToken.class);
        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(mockToken.getMember()).thenReturn(member);
        when(consentRepository.findByMemberAndConsentType(member, type)).thenReturn(Optional.empty());
        when(consentRepository.save(any(Consent.class))).thenAnswer(i -> i.getArgument(0));

        // when
        consentService.updateConsents(token, type, true);

        // then - save called twice (once in orElseGet, once after grant())
        verify(consentRepository, times(2)).save(any(Consent.class));
    }

    @Test
    void shouldUpdateExistingConsentToGranted() {
        // given
        ConsentToken mockToken = mock(ConsentToken.class);
        Consent existingConsent = Consent.create(member, type);
        existingConsent.deny(); // initially denied
        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(mockToken.getMember()).thenReturn(member);
        when(consentRepository.findByMemberAndConsentType(member, type)).thenReturn(Optional.of(existingConsent));

        // when
        consentService.updateConsents(token, type, true);

        // then
        assertThat(existingConsent.isGranted()).isTrue();
        assertThat(existingConsent.getGrantedAt()).isNotNull();
        verify(consentRepository).save(existingConsent);
    }

    @Test
    void shouldDenyConsentWhenNotExisting() {
        // given
        ConsentToken mockToken = mock(ConsentToken.class);
        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(mockToken.getMember()).thenReturn(member);
        when(consentRepository.findByMemberAndConsentType(member, type)).thenReturn(Optional.empty());
        when(consentRepository.save(any(Consent.class))).thenAnswer(i -> i.getArgument(0));

        // when
        consentService.updateConsents(token, type, false);

        // then - save called twice (once in orElseGet, once after deny())
        verify(consentRepository, times(2)).save(any(Consent.class));
    }

    @Test
    void shouldReturnFalseWhenNoConsentRecordExists() {
        // given
        when(consentRepository.findByMemberAndConsentType(member, type)).thenReturn(Optional.empty());

        // when
        boolean granted = consentService.isConsentGranted(member, type);

        // then
        assertThat(granted).isFalse();
    }

    @Test
    void shouldReturnTrueWhenConsentGranted() {
        // given
        Consent consent = Consent.create(member, type);
        consent.grant();
        when(consentRepository.findByMemberAndConsentType(member, type)).thenReturn(Optional.of(consent));

        // when
        boolean granted = consentService.isConsentGranted(member, type);

        // then
        assertThat(granted).isTrue();
    }

    @Test
    void shouldReturnFalseWhenConsentDenied() {
        // given
        Consent consent = Consent.create(member, type);
        consent.deny();
        when(consentRepository.findByMemberAndConsentType(member, type)).thenReturn(Optional.of(consent));

        // when
        boolean granted = consentService.isConsentGranted(member, type);

        // then
        assertThat(granted).isFalse();
    }

    @Test
    void shouldThrowWhenTokenNotFound() {
        // given
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // when/then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consentService.getConsentTokenByToken(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or expired token");
    }
}
