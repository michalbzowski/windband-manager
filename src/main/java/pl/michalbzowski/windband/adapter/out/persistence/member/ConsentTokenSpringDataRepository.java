package pl.michalbzowski.windband.adapter.out.persistence.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.member.ConsentToken;
import pl.michalbzowski.windband.domain.member.ConsentTokenRepository;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ConsentTokenSpringDataRepository implements ConsentTokenRepository {

    private final ConsentTokenJpaRepository jpaRepository;

    @Override
    public ConsentToken save(ConsentToken token) {
        return jpaRepository.save(token);
    }

    @Override
    public Optional<ConsentToken> findByToken(UUID token) {
        return jpaRepository.findByToken(token);
    }

    @Override
    public Optional<ConsentToken> findByMember(Member member) {
        return jpaRepository.findByMember(member);
    }

    @Override
    public void delete(ConsentToken token) {
        jpaRepository.delete(token);
    }
}
