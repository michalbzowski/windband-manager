package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BandRepositoryAdapter implements BandRepository {

    private final SpringDataBandRepository springDataRepo;

    @Override
    public Band save(Band band) {
        return springDataRepo.save(band);
    }

    @Override
    public Band saveAndFlush(Band band) {
        return springDataRepo.saveAndFlush(band);
    }

    @Override
    public Optional<Band> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public Optional<Band> findByName(String name) {
        return springDataRepo.findByName(name);
    }

    @Override
    public Optional<Band> findBySlug(String slug) {
        return springDataRepo.findBySlug(slug);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return springDataRepo.existsBySlug(slug);
    }

    @Override
    public List<Band> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public void delete(Band band) {
        springDataRepo.delete(band);
    }

    @Override
    public long count() {
        return springDataRepo.count();
    }
}
