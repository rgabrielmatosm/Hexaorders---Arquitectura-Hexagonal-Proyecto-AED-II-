package com.hexaorders.domain.fulfillment;

import com.hexaorders.domain.catalog.Product;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-side pricing of a cart. Prices never come from the client. */
public record Quote(List<OrderLine> lines, BigDecimal total) {
    public static final BigDecimal MAX_TOTAL = new BigDecimal("1000000.00");

    public Quote {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("El carrito está vacío");
        BigDecimal sum = lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) throw new IllegalArgumentException("El total no coincide con las líneas");
        if (total.compareTo(MAX_TOTAL) > 0) throw new IllegalArgumentException("El total supera 1000000.00 USD");
    }

    /** Merges repeated products, checks availability and prices every line with catalog prices. */
    public static Quote price(List<CartItem> items, Map<Long, Product> catalog) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("El carrito está vacío");
        if (items.size() > 50) throw new IllegalArgumentException("Máximo 50 líneas por pedido");
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (CartItem item : items) merged.merge(item.productId(), item.quantity(), Integer::sum);
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            Product product = catalog.get(entry.getKey());
            if (product == null || !product.details().active()) {
                throw new IllegalArgumentException("Producto no disponible: " + entry.getKey());
            }
            int quantity = entry.getValue();
            if (quantity > 99) throw new IllegalArgumentException("quantity: máximo 99 por producto");
            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(quantity)).setScale(2);
            lines.add(new OrderLine(lineNo++, product.id(), product.name(), product.price(), quantity, lineTotal));
            total = total.add(lineTotal);
        }
        return new Quote(lines, total.setScale(2));
    }
}
