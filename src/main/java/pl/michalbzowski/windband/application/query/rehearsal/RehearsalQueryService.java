package pl.michalbzowski.windband.application.query.rehearsal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.rehearsal.RehearsalNotFoundException;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.rehearsal.RehearsalRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RehearsalQueryService {

    private final RehearsalRepository rehearsalRepository;

    public Rehearsal getRehearsalById(Long id) {
        return rehearsalRepository.findById(id)
                .orElseThrow(() -> new RehearsalNotFoundException(id));
    }

    public List<Rehearsal> getAllRehearsals() {
        return rehearsalRepository.findAllOrderByDateDesc();
    }

    public List<Rehearsal> getRehearsalsBetween(LocalDate from, LocalDate to) {
        return rehearsalRepository.findByDateBetween(from, to);
    }

    public long getRehearsalCountBetween(LocalDate from, LocalDate to) {
        return rehearsalRepository.findByDateBetween(from, to).size();
    }
}
