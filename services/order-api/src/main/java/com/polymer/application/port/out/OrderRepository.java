package com.polymer.application.port.out;

import com.polymer.domain.Order;
import com.polymer.domain.OrderStatistics;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(UUID id);
    List<Order> latest(int limit);
    /** Counts every persisted order, not only the latest page. */
    OrderStatistics statistics();
}
