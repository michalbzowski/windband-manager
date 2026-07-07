package pl.michalbzowski.windband.application.query.instrument;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentQueryService {

    private final InstrumentRepository instrumentRepository;

    public List<Instrument> findAll() {
        return findAll(null);
    }

    public List<Instrument> findAll(Long teamId) {
        if (teamId != null) {
            return instrumentRepository.findAllOrderBySortPriorityByBandId(teamId);
        }
        return instrumentRepository.findAllOrderBySortPriority();
    }
}
