package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.InventoryOrder;
import pl.michalbzowski.windband.domain.inventory.OrderStatus;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataInventoryOrderRepository extends JpaRepository<InventoryOrder, Long> {
    List<InventoryOrder> findAllByOrderByCreatedAtDesc();
    List<InventoryOrder> findByRequesterOrderByCreatedAtDesc(Member requester);
    List<InventoryOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
