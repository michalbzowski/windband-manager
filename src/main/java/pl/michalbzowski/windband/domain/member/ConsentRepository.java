package pl.michalbzowski.windband.domain.member;

import java.util.Optional;
import java.util.List;

public interface ConsentRepository {
    Consent save(Consent consent);
    Optional<Consent> findByMemberAndConsentType(Member member, ConsentType consentType);
    List<Consent> findByMember(Member member);
    void delete(Consent consent);
}
