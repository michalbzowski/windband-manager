package pl.michalbzowski.windband.adapter.out.persistence.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConsentSpringDataRepository implements ConsentRepository {

    private final ConsentJpaRepository jpaRepository;

    @Override
    public Consent save(Consent consent) {
        return jpaRepository.save(consent);
    }

    @Override
    public Optional<Consent> findByMemberAndConsentType(Member member, ConsentType consentType) {
        return jpaRepository.findByMemberAndConsentType(member, consentType);
    }

    @Override
    public void delete(Consent consent) {
        jpaRepository.delete(consent);
    }
}