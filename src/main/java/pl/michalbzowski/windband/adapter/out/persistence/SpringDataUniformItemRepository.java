package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.michalbzowski.windband.domain.inventory.UniformItem;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataUniformItemRepository extends JpaRepository<UniformItem, Long> {
    List<UniformItem> findByAssignedMember(Member member);

    @Query("SELECT u FROM UniformItem u LEFT JOIN FETCH u.assignedMember")
    List<UniformItem> findAllWithMember();
}
