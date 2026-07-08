package pl.michalbzowski.windband.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentSpringDataRepository;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentTokenSpringDataRepository;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private ConsentSpringDataRepository consentRepository;

    @Mock
    private ConsentTokenSpringDataRepository tokenRepository;

    @InjectMocks
    private ConsentService consentService;

    private Member member;
    private ConsentType type;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        type = ConsentType.EVENTS;
    }

    @Test
    void shouldGrantConsentWhenNotExisting() {
        // given
        UUID token = UUID.randomUUID();
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // when
        consentService.updateConsents(token, type, true);

        // then
        verify(consentRepository).save(argThat(consent -> 
            consent.getMember() == member &&
            consent.getConsentType() == type &&
            consent.isGranted()));
        // Note: member is mock, but we expect save called with a Consent built inside service.
        // Since we mocked member, we can't assert equality directly; just verify save called.
        verify(consentRepository, times(1)).save(any(Consent.class));
    }

    @Test
    void shouldUpdateExistingConsentToGranted() {
        // given
        UUID token = UUID.randomUUID();
        Consent existingConsent = new Consent(member, type);
        existingConsent.deny(); // initially denied
        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(mock(ConsentToken.class)));
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
        UUID token = UUID.randomUUID();
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // when
        consentService.updateConsents(token, type, false);

        // then
        verify(consentRepository).save(argThat(consent -> 
            consent.getMember() == member &&
            consent.getConsentType() == type &&
            !consent.isGranted()));
        verify(consentRepository, times(1)).save(any(Consent.class));
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
        Consent consent = new Consent(member, type);
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
        Consent consent = new Consent(member, type);
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
        UUID token = UUID.randomUUID();
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> consentService.getConsentTokenByToken(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or expired token");
    }
}