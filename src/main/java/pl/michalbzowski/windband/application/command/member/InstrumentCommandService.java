package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InstrumentCommandService {

    private final InstrumentRepository instrumentRepository;
    private final BandRepository bandRepository;

    public Instrument createInstrument(String name, String description, Integer sortPriority) {
        return createInstrument(name, description, sortPriority, null);
    }

    public Instrument createInstrument(String name, String description, Integer sortPriority, Long teamId) {
        Band band = resolveBand(teamId);
        Instrument instrument = band != null ? Instrument.create(name, band) : Instrument.create(name);
        if (description != null) {
            instrument.updateDescription(description);
        }
        if (sortPriority != null) {
            instrument.updateSortPriority(sortPriority);
        }
        return instrumentRepository.save(instrument);
    }

    public Instrument updateInstrument(Long id, String name, String description, Integer sortPriority) {
        return updateInstrument(id, name, description, sortPriority, null);
    }

    public Instrument updateInstrument(Long id, String name, String description, Integer sortPriority, Long teamId) {
        Instrument instrument = resolveVisibleInstrument(id, teamId);
        instrument.updateName(name);
        if (description != null) {
            instrument.updateDescription(description);
        }
        if (sortPriority != null) {
            instrument.updateSortPriority(sortPriority);
        }
        return instrumentRepository.save(instrument);
    }

    public Instrument updateSortPriority(Long id, Integer sortPriority) {
        return updateSortPriority(id, sortPriority, null);
    }

    public Instrument updateSortPriority(Long id, Integer sortPriority, Long teamId) {
        Instrument instrument = resolveVisibleInstrument(id, teamId);
        instrument.updateSortPriority(sortPriority);
        return instrumentRepository.save(instrument);
    }

    public void deleteInstrument(Long id) {
        deleteInstrument(id, null);
    }

    public void deleteInstrument(Long id, Long teamId) {
        Instrument instrument = resolveVisibleInstrument(id, teamId);
        instrumentRepository.save(instrument);
    }

    public List<Instrument> getAllInstruments() {
        return getAllInstruments(null);
    }

    public List<Instrument> getAllInstruments(Long teamId) {
        if (teamId != null) {
            return instrumentRepository.findAllOrderBySortPriorityByBandId(teamId);
        }
        return instrumentRepository.findAll();
    }

    public Instrument getInstrumentById(Long id) {
        return getInstrumentById(id, null);
    }

    public Instrument getInstrumentById(Long id, Long teamId) {
        return resolveVisibleInstrument(id, teamId);
    }

    private Instrument resolveVisibleInstrument(Long id, Long teamId) {
        Instrument instrument = instrumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
        if (teamId != null && instrument.getBand() != null && !teamId.equals(instrument.getBand().getId())) {
            throw new IllegalArgumentException("Instrument not found: " + id);
        }
        return instrument;
    }

    private Band resolveBand(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return bandRepository.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Band not found: " + teamId));
    }
}
