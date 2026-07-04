package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemberAttributeDefRepositoryAdapter implements MemberAttributeDefRepository {

    private final SpringDataMemberAttributeDefRepository springDataRepo;

    @Override
    public MemberAttributeDef save(MemberAttributeDef def) {
        return springDataRepo.save(def);
    }

    @Override
    public Optional<MemberAttributeDef> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<MemberAttributeDef> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<MemberAttributeDef> findByBand(Band band) {
        return springDataRepo.findByBand(band);
    }

    @Override
    public List<MemberAttributeDef> findByBandOrderByDisplayOrderAsc(Band band) {
        return springDataRepo.findByBandOrderByDisplayOrderAsc(band);
    }

    @Override
    public void delete(MemberAttributeDef def) {
        springDataRepo.delete(def);
    }

    @Override
    public long countByBand(Band band) {
        return springDataRepo.countByBand(band);
    }
}
