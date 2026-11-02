package com.hexaorders.application.port.in;

import com.hexaorders.domain.Order;
import com.hexaorders.domain.OrderDraft;
import com.hexaorders.domain.OrderStatistics;
import java.util.List;
import java.util.UUID;

public interface OrderUseCases {
    Order create(OrderDraft draft);
    Order get(UUID id);
    List<Order> latest();
    OrderStatistics statistics();
}
