package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.michalbzowski.windband.domain.inventory.InventoryOrder;
import pl.michalbzowski.windband.domain.inventory.OrderStatus;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataInventoryOrderRepository extends JpaRepository<InventoryOrder, Long> {
    List<InventoryOrder> findAllByOrderByCreatedAtDesc();
    List<InventoryOrder> findByRequesterOrderByCreatedAtDesc(Member requester);
    List<InventoryOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    @Query("SELECT o FROM InventoryOrder o JOIN o.requester m WHERE m.band.id = :bandId ORDER BY o.createdAt DESC")
    List<InventoryOrder> findByBandId(Long bandId);
}
