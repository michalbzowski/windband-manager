package pl.michalbzowski.windband.domain.member;

import java.util.Objects;

/**
 * Dynamic-group source backed by a fixed domain field on {@link Member}
 * (as opposed to a user-defined attribute).
 *
 * <p>Currently supported field: {@link #ACTIVE} — the member's {@code active}
 * flag. A member matches iff the field evaluates truthy. New fixed fields
 * (e.g. "minor", "primaryInstrument=trumpet") are added here as new constants
 * plus a branch in {@link #getName()} / {@link #memberMatches(Member)}.</p>
 *
 * <p>This lets {@code Member.active} — a first-class domain field, NOT a custom
 * attribute — drive a dynamic group without converting it into an attribute
 * (which would dilute the domain model).</p>
 */
public final class MemberFieldSource implements DynamicGroupSource {

    /** Member field key for the {@code active} flag. */
    public static final String ACTIVE = "active";

    private final String field;

    public MemberFieldSource(String field) {
        this.field = Objects.requireNonNull(field, "field required");
    }

    public String getField() {
        return field;
    }

    @Override
    public String getName() {
        return switch (field) {
            case ACTIVE -> "Aktywni";
            default -> throw new IllegalArgumentException("Unknown member field source: " + field);
        };
    }

    @Override
    public boolean memberMatches(Member member) {
        return switch (field) {
            case ACTIVE -> member.isActive();
            default -> throw new IllegalArgumentException("Unknown member field source: " + field);
        };
    }
}
