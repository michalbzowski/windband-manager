package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GroupRepositoryAdapter implements GroupRepository {

    private final SpringDataGroupRepository springDataRepo;

    @Override
    public Group save(Group group) {
        return springDataRepo.save(group);
    }

    @Override
    public Optional<Group> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<Group> findAllOrderByName() {
        return springDataRepo.findAllByOrderByNameAsc();
    }

    @Override
    public List<Group> findAllWithMembers() {
        return springDataRepo.findAllWithMembers();
    }

    @Override
    public List<Group> findAllByBandId(Long bandId) {
        return springDataRepo.findAllByBandIdOrderByNameAsc(bandId);
    }

    @Override
    public List<Group> findAllWithMembersByBandId(Long bandId) {
        return springDataRepo.findAllWithMembersByBandId(bandId);
    }

    @Override
    public void delete(Group group) {
        springDataRepo.delete(group);
    }
}
