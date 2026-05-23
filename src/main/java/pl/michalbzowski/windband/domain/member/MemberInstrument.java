package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    MemberInstrument(Member member, Instrument instrument, boolean primary) {
        this.member = member;
        this.instrument = instrument;
        this.primary = primary;
    }
}
