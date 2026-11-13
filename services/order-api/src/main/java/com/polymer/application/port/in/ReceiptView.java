package com.polymer.application.port.in;

import com.polymer.domain.fulfillment.OrderLine;
import com.polymer.domain.fulfillment.ShippingAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Boleta de venta de un pedido pagado en línea (comprobante interno, no electrónico SUNAT). */
public record ReceiptView(String code, Instant issuedAt, UUID orderId, String customerName, String customerEmail, List<OrderLine> lines,
                          BigDecimal subtotal, BigDecimal savings, BigDecimal shippingCost, BigDecimal taxableBase, BigDecimal igv,
                          BigDecimal total, String currency, String paymentMethod, String reference, String cardBrand, String cardLast4,
                          String deliveryMethod, ShippingAddress shipping) {}
