package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.UniformItem;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataUniformItemRepository extends JpaRepository<UniformItem, Long> {
    List<UniformItem> findByAssignedMember(Member member);
}
