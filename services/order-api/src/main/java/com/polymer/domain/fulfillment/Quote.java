package com.polymer.domain.fulfillment;

import com.polymer.domain.catalog.PriceTag;
import com.polymer.domain.catalog.Product;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Server-side pricing of a cart. Prices never come from the client. total is the sum of the lines. */
public record Quote(List<OrderLine> lines, BigDecimal total) {
    public static final BigDecimal MAX_TOTAL = new BigDecimal("1000000.00");

    public Quote {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("El carrito está vacío");
        BigDecimal sum = lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) throw new IllegalArgumentException("El total no coincide con las líneas");
        if (total.compareTo(MAX_TOTAL) > 0) throw new IllegalArgumentException("El total supera 1000000.00");
    }

    /** Units in the cart. */
    public int units() { return lines.stream().mapToInt(OrderLine::quantity).sum(); }

    /** Savings against crossed-out prices (promotions and previous prices). */
    public BigDecimal savings() { return lines.stream().map(OrderLine::savings).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add); }

    /** Regular catalog prices, without promotions (v2–v5 behaviour). */
    public static Quote price(List<CartItem> items, Map<Long, Product> catalog) {
        return price(items, catalog, PriceTag::regular);
    }

    /** Merges repeated products, checks availability and prices every line with the price the customer pays now. */
    public static Quote price(List<CartItem> items, Map<Long, Product> catalog, Function<Product, PriceTag> pricing) {
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
            PriceTag tag = pricing.apply(product);
            BigDecimal lineTotal = tag.price().multiply(BigDecimal.valueOf(quantity)).setScale(2);
            lines.add(new OrderLine(lineNo++, product.id(), product.name(), tag.price(), quantity, lineTotal,
                    tag.previousPrice(), tag.promotionId()));
            total = total.add(lineTotal);
        }
        return new Quote(lines, total.setScale(2));
    }
}
