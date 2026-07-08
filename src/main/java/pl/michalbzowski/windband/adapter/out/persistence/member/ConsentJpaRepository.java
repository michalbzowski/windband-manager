package pl.michalbzowski.windband.adapter.out.persistence.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentJpaRepository extends JpaRepository<Consent, Long> {
    Optional<Consent> findByMemberAndConsentType(Member member, ConsentType consentType);
    List<Consent> findByMember(Member member);
}
