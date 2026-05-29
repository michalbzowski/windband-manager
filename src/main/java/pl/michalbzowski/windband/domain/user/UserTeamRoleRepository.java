package pl.michalbzowski.windband.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserTeamRoleRepository {

    UserTeamRole save(UserTeamRole role);

    Optional<UserTeamRole> findByUserIdAndTeamId(Long userId, Long teamId);

    List<UserTeamRole> findByUserId(Long userId);

    List<UserTeamRole> findByTeamId(Long teamId);

    Optional<UserTeamRole> findByInvitationToken(String token);

    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    void delete(UserTeamRole role);

    long countByTeamIdAndRole(Long teamId, TeamRole role);
}
