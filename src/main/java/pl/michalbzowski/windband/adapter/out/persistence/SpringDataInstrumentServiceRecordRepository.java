package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.inventory.InstrumentServiceRecord;
import pl.michalbzowski.windband.domain.inventory.ServiceStatus;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataInstrumentServiceRecordRepository extends JpaRepository<InstrumentServiceRecord, Long> {

    List<InstrumentServiceRecord> findByInstrumentId(Long instrumentId);

    List<InstrumentServiceRecord> findByBandId(Long bandId);

    List<InstrumentServiceRecord> findByBandIdAndStatus(Long bandId, ServiceStatus status);

    List<InstrumentServiceRecord> findByBandIdAndServiceDateBetween(Long bandId, LocalDate from, LocalDate to);

    @Query("SELECT r FROM InstrumentServiceRecord r WHERE r.band.id = :bandId AND r.status IN ('SCHEDULED','APPROVED','IN_PROGRESS') AND r.serviceDate < CURRENT_DATE")
    List<InstrumentServiceRecord> findOverdue(@Param("bandId") Long bandId);

    @Query("SELECT r FROM InstrumentServiceRecord r WHERE r.band.id = :bandId AND r.status IN ('SCHEDULED','APPROVED') AND r.serviceDate <= :beforeDate")
    List<InstrumentServiceRecord> findUpcoming(@Param("bandId") Long bandId, @Param("beforeDate") LocalDate beforeDate);
}
