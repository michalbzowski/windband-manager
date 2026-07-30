package pl.michalbzowski.windband.application.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberReportDto {

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String instrumentName;
    private LocalDate joinedDate;
    private Boolean active;
    private Integer age;

    public static MemberReportDto fromMember(Member member) {
        String instrumentName = member.getPrimaryInstrument()
                .map(Instrument::getName)
                .orElse("—");
        return new MemberReportDto(
                member.getFirstName(),
                member.getLastName(),
                member.getEmail(),
                member.getPhone(),
                instrumentName,
                member.getJoinedDate(),
                member.isActive(),
                member.getAge()
        );
    }
}