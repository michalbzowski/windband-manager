package pl.michalbzowski.windband.domain.member;

/**
 * Domain event published when a {@link Member} becomes active (reactivated after
 * having been deactivated, or activated for the first time outside of creation).
 *
 * <p>Carries the member id + band id so listeners can act without re-loading the
 * entity. Intentionally a plain object (no Spring coupling) — Spring's
 * {@code ApplicationEventPublisher} publishes any object as an event.</p>
 *
 * <p>Extensions: a future notification listener can subscribe to this (and
 * {@link MemberDeactivatedEvent}) to send emails / portal messages on status
 * changes, without any change to the member lifecycle code.</p>
 */
public record MemberActivatedEvent(Long memberId, Long bandId) {
}
