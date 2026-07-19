package pl.michalbzowski.windband.domain.member;

/**
 * Domain event published when a {@link Member} is deactivated (leaves the active
 * roster). See {@link MemberActivatedEvent} for the design rationale and the
 * future notification-use-case hook.
 */
public record MemberDeactivatedEvent(Long memberId, Long bandId) {
}
