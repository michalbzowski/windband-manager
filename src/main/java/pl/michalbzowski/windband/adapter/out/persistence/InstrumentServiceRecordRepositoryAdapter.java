package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.InstrumentServiceRecord;
import pl.michalbzowski.windband.domain.inventory.InstrumentServiceRecordRepository;
import pl.michalbzowski.windband.domain.inventory.ServiceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InstrumentServiceRecordRepositoryAdapter implements InstrumentServiceRecordRepository {

    private final SpringDataInstrumentServiceRecordRepository springDataRepo;

    @Override
    public InstrumentServiceRecord save(InstrumentServiceRecord record) {
        return springDataRepo.save(record);
    }

    @Override
    public Optional<InstrumentServiceRecord> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<InstrumentServiceRecord> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<InstrumentServiceRecord> findByInstrumentId(Long instrumentId) {
        return springDataRepo.findByInstrumentId(instrumentId);
    }

    @Override
    public List<InstrumentServiceRecord> findByBandId(Long bandId) {
        return springDataRepo.findByBandId(bandId);
    }

    @Override
    public List<InstrumentServiceRecord> findByBandIdAndStatus(Long bandId, ServiceStatus status) {
        return springDataRepo.findByBandIdAndStatus(bandId, status);
    }

    @Override
    public List<InstrumentServiceRecord> findByBandIdAndDateRange(Long bandId, LocalDate from, LocalDate to) {
        return springDataRepo.findByBandIdAndServiceDateBetween(bandId, from, to);
    }

    @Override
    public List<InstrumentServiceRecord> findOverdue(Long bandId) {
        return springDataRepo.findOverdue(bandId);
    }

    @Override
    public List<InstrumentServiceRecord> findUpcomingServices(Long bandId, LocalDate beforeDate) {
        return springDataRepo.findUpcoming(bandId, beforeDate);
    }

    @Override
    public void delete(InstrumentServiceRecord record) {
        springDataRepo.delete(record);
    }
}
