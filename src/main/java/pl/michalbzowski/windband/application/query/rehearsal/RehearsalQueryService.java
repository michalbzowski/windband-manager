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
        return getAllRehearsals(null);
    }

    public List<Rehearsal> getAllRehearsals(Long teamId) {
        if (teamId != null) {
            return rehearsalRepository.findAllOrderByDateDescByBandId(teamId);
        }
        return rehearsalRepository.findAllOrderByDateDesc();
    }

    public List<Rehearsal> getRehearsalsBetween(LocalDate from, LocalDate to) {
        return getRehearsalsBetween(from, to, null);
    }

    public List<Rehearsal> getRehearsalsBetween(LocalDate from, LocalDate to, Long teamId) {
        if (teamId != null) {
            return rehearsalRepository.findByDateBetweenAndBandId(from, to, teamId);
        }
        return rehearsalRepository.findByDateBetween(from, to);
    }

    /**
     * Upcoming (today or later) rehearsals, sorted nearest-first (ascending by date).
     */
    public List<Rehearsal> getUpcomingRehearsals(Long teamId) {
        LocalDate today = LocalDate.now();
        return loadSortedAsc(teamId).stream()
                .filter(r -> !r.getDate().isBefore(today))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Past (before today) rehearsals, sorted most-recent-first (descending by date),
     * rendered after the upcoming section.
     */
    public List<Rehearsal> getPastRehearsals(Long teamId) {
        LocalDate today = LocalDate.now();
        return loadSortedAsc(teamId).stream()
                .filter(r -> r.getDate().isBefore(today))
                .sorted(java.util.Comparator.comparing(Rehearsal::getDate).reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Rehearsal> loadSortedAsc(Long teamId) {
        List<Rehearsal> all = (teamId != null)
                ? rehearsalRepository.findAllOrderByDateDescByBandId(teamId)
                : rehearsalRepository.findAllOrderByDateDesc();
        all.sort(java.util.Comparator.comparing(Rehearsal::getDate));
        return all;
    }

    public long getRehearsalCountBetween(LocalDate from, LocalDate to) {
        return getRehearsalCountBetween(from, to, null);
    }

    public long getRehearsalCountBetween(LocalDate from, LocalDate to, Long teamId) {
        return getRehearsalsBetween(from, to, teamId).size();
    }
}
