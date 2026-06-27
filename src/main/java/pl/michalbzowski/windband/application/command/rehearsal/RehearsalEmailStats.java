package pl.michalbzowski.windband.application.command.rehearsal;

import java.util.List;

public record RehearsalEmailStats(
        int totalMembers,
        int successCount,
        int failedCount,
        List<MemberEmailResult> memberResults
) {
    public record MemberEmailResult(
            Long memberId,
            String firstName,
            String lastName,
            String email,
            boolean success
    ) {}
}
