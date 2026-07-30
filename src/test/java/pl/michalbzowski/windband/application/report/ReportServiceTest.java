package pl.michalbzowski.windband.application.report;

import net.sf.jasperreports.engine.JRException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void shouldGenerateMembersPdfForExistingBand() throws JRException, IOException {
        // given - use existing test data from data.sql (band_id = 1)
        Long bandId = 1L;

        // when
        byte[] pdf = reportService.generateMembersPdf(bandId);

        // then
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
        assertArrayStartsWithPercentPDF(pdf);
    }

    @Test
    void shouldReturnEmptyForNonExistentBand() {
        // given
        Long nonExistentBandId = 99999L;

        // when
        Optional<byte[]> result = reportService.generateMembersPdfSafe(nonExistentBandId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowExceptionForNonExistentBandWhenUsingUnsafeMethod() {
        // given
        Long nonExistentBandId = 99999L;

        // when / then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> reportService.generateMembersPdf(nonExistentBandId));
        assertThat(exception.getMessage()).contains("Band not found");
    }

    @Test
    void shouldIncludeMemberDataInGeneratedPdf() throws JRException, IOException {
        // given - create a fresh band and member to ensure clean test data
        Band band = Band.create("Test Band " + System.currentTimeMillis(), "test-band-" + System.currentTimeMillis());
        Band savedBand = bandRepository.saveAndFlush(band);

        // Create and save instrument with band first
        Instrument instrument = Instrument.create("Test Instrument", savedBand);
        Instrument savedInstrument = instrumentRepository.save(instrument);

        Member member = Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 15), savedBand);
        member.updateContact("jan@example.com", "123456789", true);
        member.addInstrument(savedInstrument, true);
        Member savedMember = memberRepository.saveAndFlush(member);

        // when
        byte[] pdf = reportService.generateMembersPdf(savedBand.getId());

        // then
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }

    private void assertArrayStartsWithPercentPDF(byte[] pdf) {
        assertThat(pdf[0]).isEqualTo((byte) '%');
        assertThat(pdf[1]).isEqualTo((byte) 'P');
        assertThat(pdf[2]).isEqualTo((byte) 'D');
        assertThat(pdf[3]).isEqualTo((byte) 'F');
        assertThat(pdf[4]).isEqualTo((byte) '-');
    }
}
