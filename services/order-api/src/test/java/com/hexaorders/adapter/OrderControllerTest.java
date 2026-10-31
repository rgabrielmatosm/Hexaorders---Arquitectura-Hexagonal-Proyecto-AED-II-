package com.hexaorders.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexaorders.adapter.in.web.ApiExceptionHandler;
import com.hexaorders.adapter.in.web.OrderController;
import com.hexaorders.application.exception.OrderNotFoundException;
import com.hexaorders.application.exception.RiskUnavailableException;
import com.hexaorders.application.port.in.OrderUseCases;
import com.hexaorders.domain.Order;
import com.hexaorders.domain.OrderDraft;
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
    void missingOrderReturns404AndMalformedIdReturns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCases.get(id)).thenThrow(new OrderNotFoundException(id));
        http.perform(get("/api/orders/" + id)).andExpect(status().isNotFound());
        http.perform(get("/api/orders/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
