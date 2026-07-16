package pl.michalbzowski.windband.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.ConsentToken;
import pl.michalbzowski.windband.domain.member.ConsentTokenRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final ConsentTokenRepository consentTokenRepository;

    @Transactional(readOnly = true)
    public ConsentToken getConsentTokenByToken(UUID token) {
        return consentTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
    }

    /**
     * Loads everything the consent page needs in a single transaction, so the controller
     * never touches lazy associations (member.getBand()) outside a Hibernate session.
     */
    @Transactional(readOnly = true)
    public ConsentPageData getConsentPageData(UUID token) {
        ConsentToken consentToken = consentTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
        Member member = consentToken.getMember();
        String memberName = member.getFirstName() + " " + member.getLastName();
        String teamName = member.getBand() != null ? member.getBand().getName() : "Nieznany zespół";

        Map<ConsentType, Boolean> consentMap = new EnumMap<>(ConsentType.class);
        for (ConsentType type : ConsentType.values()) {
            consentMap.put(type, isConsentGranted(member, type));
        }
        return new ConsentPageData(memberName, teamName, token, consentMap);
    }

    @Transactional
    public void updateConsents(UUID token, ConsentType type, boolean granted) {
        ConsentToken ct = consentTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        Member member = ct.getMember();

        Consent consent = consentRepository.findByMemberAndConsentType(member, type)
                .orElseGet(() -> {
                    Consent c = Consent.create(member, type);
                    return consentRepository.save(c);
                });
        if (granted) {
            consent.grant();
        } else {
            consent.deny(); // optional, as default is false
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