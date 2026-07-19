package pl.michalbzowski.windband.domain.member;

/**
 * Discriminates what a dynamic {@link Group} is derived from.
 *
 * <ul>
 *   <li>{@code ATTRIBUTE} — backed by a user-defined {@code MemberAttributeDef}
 *       (always of type BOOLEAN). The source reference is the attribute def id,
 *       stored historically in {@code Group.dynamicSource}.</li>
 *   <li>{@code MEMBER_FIELD} — backed by a fixed domain field on {@link Member}
 *       (e.g. {@code active}). The source reference is the field name, stored in
 *       {@code Group.dynamicSourceKey}.</li>
 * </ul>
 *
 * This is the Open/Closed-friendly extension point: adding a new derived group
 * (e.g. "Minors", "Primary instrument = trumpet") means adding another value
 * here plus a branch in {@link MemberFieldSource}, with no changes to the
 * sync orchestration in {@link pl.michalbzowski.windband.application.command.member.GroupCommandService}.
 */
public enum DynamicSourceType {
    ATTRIBUTE,
    MEMBER_FIELD
}
