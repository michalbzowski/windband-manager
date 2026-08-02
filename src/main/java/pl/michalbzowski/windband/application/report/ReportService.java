package pl.michalbzowski.windband.application.report;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final MemberRepository memberRepository;
    private final BandRepository bandRepository;
    private final ReportCompiler reportCompiler;

    public byte[] generateMembersPdf(Long bandId) throws JRException, IOException {
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + bandId));

        // FIX: Pass Band object instead of ID to match SpringDataMemberRepository signature
        List<Member> members = memberRepository.findAllByBandOrderByLastNameAscFirstNameAsc(band);
        List<MemberReportDto> memberDtos = members.stream()
                .map(MemberReportDto::fromMember)
                .toList();

        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(memberDtos);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("BAND_NAME", band.getName());
        parameters.put("GENERATED_DATE", LocalDate.now());

        JasperReport report = reportCompiler.getCompiledReport("members");
        if (report == null) {
            throw new JRException("Compiled report not available: members");
        }

        JasperPrint jasperPrint = JasperFillManager.fillReport(report, parameters, dataSource);
        return JasperExportManager.exportReportToPdf(jasperPrint);
    }

    public Optional<byte[]> generateMembersPdfSafe(Long bandId) {
        try {
            return Optional.of(generateMembersPdf(bandId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}