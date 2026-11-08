package com.polymer.application.port.in;

import com.polymer.domain.Order;
import com.polymer.domain.OrderDraft;
import com.polymer.domain.OrderStatistics;
import java.util.List;
import java.util.UUID;

public interface OrderUseCases {
    Order create(OrderDraft draft);
    Order get(UUID id);
    List<Order> latest();
    OrderStatistics statistics();
}
