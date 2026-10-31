package com.hexaorders.application.port.out;

import com.hexaorders.domain.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(UUID id);
    List<Order> latest(int limit);
}
