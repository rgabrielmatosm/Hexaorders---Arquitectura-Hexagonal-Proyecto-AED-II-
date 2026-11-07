package com.hexaorders.application.service;

import com.hexaorders.application.exception.BusinessConflictException;
import com.hexaorders.application.exception.ResourceNotFoundException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.CartUseCases;
import com.hexaorders.application.port.out.CartRepository;
import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.domain.catalog.PriceTag;
import com.hexaorders.domain.catalog.PricedProduct;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.identity.Role;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Saved cart of a customer. Quantities are validated against live stock when they change; prices
 * are always recalculated (promotions included), so the cart never shows a stale price.
 */
public final class CartService implements CartUseCases {
    static final int MAX_LINES = 50;
    private final CartRepository carts;
    private final CatalogRepository catalog;
    private final PricingService pricing;

    public CartService(CartRepository carts, CatalogRepository catalog, PricingService pricing) {
        this.carts = carts;
        this.catalog = catalog;
        this.pricing = pricing;
    }

    @Override
    public CartView cart(AuthenticatedUser customer) {
        Guards.require(customer, Role.CUSTOMER);
        return view(carts.items(customer.userId()));
    }

    @Override
    public CartView setItem(AuthenticatedUser customer, long productId, int quantity) {
        Guards.require(customer, Role.CUSTOMER);
        if (quantity <= 0) return removeItem(customer, productId);
        requireQuantity(productId, quantity, carts.items(customer.userId()));
        carts.set(customer.userId(), productId, quantity);
        return view(carts.items(customer.userId()));
    }

    @Override
    public CartView addItem(AuthenticatedUser customer, long productId, int quantity) {
        Guards.require(customer, Role.CUSTOMER);
        if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("quantity: entre 1 y 99");
        List<CartItem> items = carts.items(customer.userId());
        int current = items.stream().filter(i -> i.productId() == productId).mapToInt(CartItem::quantity).sum();
        int target = Math.min(99, current + quantity);
        requireQuantity(productId, target, items);
        carts.set(customer.userId(), productId, target);
        return view(carts.items(customer.userId()));
    }

    @Override
    public CartView removeItem(AuthenticatedUser customer, long productId) {
        Guards.require(customer, Role.CUSTOMER);
        carts.remove(customer.userId(), productId);
        return view(carts.items(customer.userId()));
    }

    @Override
    public CartView clear(AuthenticatedUser customer) {
        Guards.require(customer, Role.CUSTOMER);
        carts.clear(customer.userId());
        return view(List.of());
    }

    @Override
    public CartView merge(AuthenticatedUser customer, List<CartItem> items) {
        Guards.require(customer, Role.CUSTOMER);
        if (items == null || items.isEmpty()) return cart(customer);
        if (items.size() > MAX_LINES) throw new IllegalArgumentException("Máximo " + MAX_LINES + " productos distintos en el carrito");
        Map<Long, Integer> merged = new LinkedHashMap<>();
        carts.items(customer.userId()).forEach(i -> merged.merge(i.productId(), i.quantity(), Integer::sum));
        items.forEach(i -> merged.merge(i.productId(), i.quantity(), Integer::sum));
        Map<Long, Product> products = catalog.productsByIds(merged.keySet());
        merged.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product == null || !product.details().active() || product.stock() <= 0) return; // nothing to keep
            carts.set(customer.userId(), productId, Math.min(Math.min(99, quantity), product.stock()));
        });
        return view(carts.items(customer.userId()));
    }

    private void requireQuantity(long productId, int quantity, List<CartItem> items) {
        if (quantity > 99) throw new IllegalArgumentException("quantity: máximo 99 por producto");
        Product product = catalog.product(productId).filter(p -> p.details().active())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no disponible"));
        boolean newLine = items.stream().noneMatch(i -> i.productId() == productId);
        if (newLine && items.size() >= MAX_LINES) throw new IllegalArgumentException("Máximo " + MAX_LINES + " productos distintos en el carrito");
        if (product.stock() <= 0) throw new BusinessConflictException("OUT_OF_STOCK", product.name() + " está agotado");
        if (quantity > product.stock()) {
            throw new BusinessConflictException("INSUFFICIENT_STOCK", "Solo quedan " + product.stock() + " unidades de " + product.name());
        }
    }

    private CartView view(List<CartItem> items) {
        Map<Long, Product> products = catalog.productsByIds(items.stream().map(CartItem::productId).toList());
        Function<Product, PriceTag> pricer = pricing.pricer();
        List<CartLine> lines = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal savings = BigDecimal.ZERO.setScale(2);
        int units = 0;
        for (CartItem item : items) {
            Product product = products.get(item.productId());
            if (product == null) continue;
            PriceTag tag = pricer.apply(product);
            BigDecimal lineTotal = tag.price().multiply(BigDecimal.valueOf(item.quantity())).setScale(2);
            boolean purchasable = product.available(item.quantity());
            if (!product.details().active()) problems.add(product.name() + " ya no está a la venta");
            else if (product.stock() <= 0) problems.add(product.name() + " está agotado");
            else if (!purchasable) problems.add("Solo quedan " + product.stock() + " unidades de " + product.name());
            lines.add(new CartLine(new PricedProduct(product, tag), item.quantity(), lineTotal, purchasable));
            if (purchasable) {
                subtotal = subtotal.add(lineTotal);
                savings = savings.add(tag.savings().multiply(BigDecimal.valueOf(item.quantity())));
                units += item.quantity();
            }
        }
        return new CartView(lines, units, subtotal, savings, problems);
    }
}
