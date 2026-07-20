package pl.michalbzowski.windband.domain.member;

import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;

import java.util.Objects;

/**
 * Dynamic-group source backed by a user-defined BOOLEAN {@link MemberAttributeDef}.
 * A member matches iff the attribute value (for that member) equals "true".
 *
 * <p>Depends only on domain types — including the {@code MemberAttributeValueRepository}
 * interface — so it stays inside the domain layer (ArchUnit-clean).</p>
 */
public final class AttributeDefSource implements DynamicGroupSource {

    private final MemberAttributeDef def;
    private final MemberAttributeValueRepository valueRepository;

    public AttributeDefSource(MemberAttributeDef def, MemberAttributeValueRepository valueRepository) {
        this.def = Objects.requireNonNull(def, "def required");
        this.valueRepository = Objects.requireNonNull(valueRepository, "valueRepository required");
    }

    public MemberAttributeDef getDef() {
        return def;
    }

    @Override
    public String getName() {
        return def.getName();
    }

    @Override
    public boolean memberMatches(Member member) {
        return valueRepository.findByMemberAndAttributeDef(member, def)
                .map(v -> "true".equalsIgnoreCase(v.getValue()))
                .orElse(false);
    }
}
