package pl.michalbzowski.windband.application.report;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.io.IOException;
import java.io.InputStream;
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
    private final ResourceLoader resourceLoader;

    public byte[] generateMembersPdf(Long bandId) throws JRException, IOException {
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + bandId));

        List<Member> members = memberRepository.findByBandIdOrderByLastNameAscFirstNameAsc(bandId);
        List<MemberReportDto> memberDtos = members.stream()
                .map(MemberReportDto::fromMember)
                .toList();

        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(memberDtos);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("BAND_NAME", band.getName());
        parameters.put("GENERATED_DATE", LocalDate.now());

        InputStream reportStream = resourceLoader
                .getResource("classpath:reports/members.jasper")
                .getInputStream();

        JasperPrint jasperPrint = JasperFillManager.fillReport(reportStream, parameters, dataSource);
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