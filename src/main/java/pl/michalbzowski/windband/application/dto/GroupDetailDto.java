package pl.michalbzowski.windband.application.dto;

import java.util.List;

public record GroupDetailDto(
        Long id,
        String name,
        String description,
        int memberCount,
        List<GroupMemberDto> members,
        boolean dynamic
) {
    public record GroupMemberDto(
            Long id,
            Long memberId,
            String memberName,
            String primaryInstrument
    ) {}
}
