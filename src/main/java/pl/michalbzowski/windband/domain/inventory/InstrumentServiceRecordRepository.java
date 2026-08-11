package pl.michalbzowski.windband.domain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstrumentServiceRecordRepository {

    InstrumentServiceRecord save(InstrumentServiceRecord record);

    Optional<InstrumentServiceRecord> findById(Long id);

    List<InstrumentServiceRecord> findAll();

    List<InstrumentServiceRecord> findByInstrumentId(Long instrumentId);

    List<InstrumentServiceRecord> findByBandId(Long bandId);

    List<InstrumentServiceRecord> findByBandIdAndStatus(Long bandId, ServiceStatus status);

    List<InstrumentServiceRecord> findByBandIdAndDateRange(Long bandId, LocalDate from, LocalDate to);

    List<InstrumentServiceRecord> findOverdue(Long bandId);

    List<InstrumentServiceRecord> findUpcomingServices(Long bandId, LocalDate beforeDate);

    void delete(InstrumentServiceRecord record);
}