package pl.michalbzowski.windband.adapter.out.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.user.TeamRole;
import pl.michalbzowski.windband.domain.user.UserTeamRole;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataUserTeamRoleRepository extends JpaRepository<UserTeamRole, Long>, UserTeamRoleRepository {

    @Override
    Optional<UserTeamRole> findByUserIdAndTeamId(Long userId, Long teamId);

    @Override
    List<UserTeamRole> findByUserId(Long userId);

    @Override
    List<UserTeamRole> findByTeamId(Long teamId);

    @Override
    Optional<UserTeamRole> findByInvitationToken(String token);

    @Override
    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    @Override
    long countByTeamIdAndRole(Long teamId, TeamRole role);
}
