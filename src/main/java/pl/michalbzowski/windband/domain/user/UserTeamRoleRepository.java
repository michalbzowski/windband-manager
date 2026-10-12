package pl.michalbzowski.windband.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserTeamRoleRepository {

    UserTeamRole save(UserTeamRole role);

    Optional<UserTeamRole> findByUserIdAndTeamId(Long userId, Long teamId);

    List<UserTeamRole> findByUserId(Long userId);

    /** User's role entries with their bands preloaded (eager): callers outside a
     * Hibernate session must read band fields without lazy-init failure. */
    List<UserTeamRole> loadWithBandsByUserId(Long userId);

    List<UserTeamRole> findByTeamId(Long teamId);

    Optional<UserTeamRole> findByInvitationToken(String token);

    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    void delete(UserTeamRole role);

    long countByTeamIdAndRole(Long teamId, TeamRole role);
}
