package pl.michalbzowski.windband.domain.inventory;

public enum OrderStatus {
    SUBMITTED,           // ZŁOŻONO
    PENDING_APPROVAL,    // PRZEKAZANO DO AKCEPTACJI
    IN_PRODUCTION,       // PRZEKAZANO DO REALIZACJI
    SHIPPED,             // WYSŁANO
    DELIVERED,           // ODEBRANO
    CANCELLED            // ANULOWANO
}
