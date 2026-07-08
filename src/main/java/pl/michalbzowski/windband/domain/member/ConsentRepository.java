package pl.michalbzowski.windband.domain.member;

import java.util.Optional;

public interface ConsentRepository {
    Consent save(Consent consent);
    Optional<Consent> findByMemberAndConsentType(Member member, ConsentType consentType);
    void delete(Consent consent);
}