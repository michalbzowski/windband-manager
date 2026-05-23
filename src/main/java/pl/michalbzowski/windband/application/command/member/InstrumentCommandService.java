package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InstrumentCommandService {

    private final InstrumentRepository instrumentRepository;

    public Instrument createInstrument(String name, String description) {
        Instrument instrument = Instrument.create(name);
        if (description != null) {
            instrument.updateDescription(description);
        }
        return instrumentRepository.save(instrument);
    }

    public Instrument updateInstrument(Long id, String name, String description) {
        Instrument instrument = instrumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
        instrument.updateName(name);
        instrument.updateDescription(description);
        return instrumentRepository.save(instrument);
    }

    public void deleteInstrument(Long id) {
        Instrument instrument = instrumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
        instrumentRepository.save(instrument);
        // Note: delete handled via repository if needed
    }

    public List<Instrument> getAllInstruments() {
        return instrumentRepository.findAll();
    }

    public Instrument getInstrumentById(Long id) {
        return instrumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }
}
