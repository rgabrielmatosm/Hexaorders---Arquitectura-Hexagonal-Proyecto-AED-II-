package com.polymer.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polymer.adapter.in.web.ApiExceptionHandler;
import com.polymer.adapter.in.web.OrderController;
import com.polymer.application.exception.OrderNotFoundException;
import com.polymer.application.exception.RiskUnavailableException;
import com.polymer.application.port.in.OrderUseCases;
import com.polymer.domain.Order;
import com.polymer.domain.OrderDraft;
import com.polymer.domain.OrderStatistics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerTest {
    private final OrderUseCases useCases = mock(OrderUseCases.class);
    private MockMvc http;

    @BeforeEach
    void setup() {
        http = MockMvcBuilders.standaloneSetup(new OrderController(useCases))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void createsOrderWithLocationHeader() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCases.create(any())).thenReturn(Order.create(
                new OrderDraft("demo", new BigDecimal("10.00"), "USD"), 20, id, Instant.now()));
        http.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"demo\",\"amount\":10.00}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/orders/" + id))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectsAmountsWithThreeDecimals() throws Exception {
        http.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"demo\",\"amount\":10.001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unavailableRiskReturns503() throws Exception {
        when(useCases.create(any())).thenThrow(new RiskUnavailableException("timeout"));
        http.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"demo\",\"amount\":10.00}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void statisticsEndpointIsNotMistakenForAnOrderId() throws Exception {
        when(useCases.statistics()).thenReturn(OrderStatistics.of(3, 1));
        http.perform(get("/api/orders/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.approved").value(3))
                .andExpect(jsonPath("$.review").value(1));
    }

    @Test
    void missingOrderReturns404AndMalformedIdReturns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCases.get(id)).thenThrow(new OrderNotFoundException(id));
        http.perform(get("/api/orders/" + id)).andExpect(status().isNotFound());
        http.perform(get("/api/orders/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
