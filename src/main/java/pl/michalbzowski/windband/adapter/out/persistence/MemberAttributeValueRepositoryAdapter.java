package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemberAttributeValueRepositoryAdapter implements MemberAttributeValueRepository {

    private final SpringDataMemberAttributeValueRepository springDataRepo;

    @Override
    public MemberAttributeValue save(MemberAttributeValue value) {
        return springDataRepo.save(value);
    }

    @Override
    public Optional<MemberAttributeValue> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public Optional<MemberAttributeValue> findByMemberAndAttributeDef(Member member, MemberAttributeDef attributeDef) {
        return springDataRepo.findByMemberAndAttributeDef(member, attributeDef);
    }

    @Override
    public List<MemberAttributeValue> findByMember(Member member) {
        return springDataRepo.findByMember(member);
    }

    @Override
    public void delete(MemberAttributeValue value) {
        springDataRepo.delete(value);
    }

    @Override
    public void deleteByMember(Member member) {
        springDataRepo.deleteByMember(member);
    }
}
