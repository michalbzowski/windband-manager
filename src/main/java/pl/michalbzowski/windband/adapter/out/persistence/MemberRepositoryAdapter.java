package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final SpringDataMemberRepository springDataRepo;

    @Override
    public Member save(Member member) {
        return springDataRepo.save(member);
    }

    @Override
    public Member saveAndFlush(Member member) {
        return springDataRepo.saveAndFlush(member);
    }

    @Override
    public Optional<Member> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<Member> findAllActive() {
        return springDataRepo.findByActiveTrue();
    }

    @Override
    public List<Member> findAllActiveByBandId(Long bandId) {
        return springDataRepo.findByActiveTrueAndBandId(bandId);
    }

    @Override
    public List<Member> findByInstrument(Instrument instrument) {
        return springDataRepo.findAll().stream()
                .filter(m -> m.getAllInstruments().contains(instrument))
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return springDataRepo.existsById(id);
    }

    @Override
    public void delete(Member member) {
        springDataRepo.delete(member);
    }
}
