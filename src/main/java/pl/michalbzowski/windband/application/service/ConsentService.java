package pl.michalbzowski.windband.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentSpringDataRepository;
import pl.michalbazarder.windband.adapter.out.persistence.member.ConsentTokenSpringDataRepository;
import pl.michalbazarder.windband.domain.member.Consent;
import pl.michalbazarder.windband.domain.member.ConsentToken;
import pl.michalbazarder.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentService {

    private final ConsentSpringDataRepository consentRepository;
    private final ConsentTokenSpringDataRepository consentTokenRepository;

    @Transactional(readOnly = true)
    public ConsentToken getConsentTokenByToken(UUID token) {
        return consentTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
    }

    @Transactional
    public void updateConsents(UUID token, ConsentType type, boolean granted) {
        ConsentToken ct = consentTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        Member member = ct.getMember();

        Consent consent = consentRepository.findByMemberAndConsentType(member, type)
                .orElseGet(() -> {
                    Consent c = new Consent(member, type, false);
                    return consentRepository.save(c);
                });
        if (granted) {
            consent.grant();
        } else {
            consent.deny();
        }
        consentRepository.save(consent);
    }

    @Transactional(readOnly = true)
    public boolean isConsentGranted(Member member, ConsentType type) {
        return consentRepository.findByMemberAndConsentType(member, type)
                .map(Consent::isGranted)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Consent> getAllConsentsForMember(Member member) {
        return consentRepository.findByMember(member);
    }
}