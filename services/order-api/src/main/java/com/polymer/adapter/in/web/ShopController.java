package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.CartUseCases;
import com.polymer.application.port.in.CommerceUseCases;
import com.polymer.application.port.in.OrderDetails;
import com.polymer.application.port.in.PlacementResult;
import com.polymer.domain.fulfillment.CartItem;
import com.polymer.domain.fulfillment.CheckoutSummary;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.DeliveryMethod;
import com.polymer.domain.fulfillment.OrderLine;
import com.polymer.domain.fulfillment.PaymentMethod;
import com.polymer.domain.fulfillment.Quote;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer portal: server-side pricing, checkout (idempotent), own orders and cancellation.
 * v6: saved cart, checkout summary with delivery and payment methods, prices in PEN.
 */
@RestController
@RequestMapping("/api/shop")
public final class ShopController {
    private final CommerceUseCases commerce;
    private final CartUseCases carts;

    public ShopController(CommerceUseCases commerce, CartUseCases carts) {
        this.commerce = commerce;
        this.carts = carts;
    }

    @PostMapping("/quote")
    public Quote quote(@RequestBody CartRequest cart) { return commerce.quote(cart.toItems()); }

    /** v6 (public): totals with promotions and the shipping cost of the chosen delivery method. */
    @PostMapping("/checkout-summary")
    public SummaryResponse summary(@RequestBody CartRequest cart) {
        CheckoutSummary summary = commerce.checkout(cart.toItems(), DeliveryMethod.parse(cart.deliveryMethod()),
                PaymentMethod.parse(cart.paymentMethod()));
        return SummaryResponse.of(summary);
    }

    /** v6 (public): delivery and payment methods with their rules. */
    @GetMapping("/checkout-options")
    public CheckoutOptions options() { return CheckoutOptions.current(); }

    @PostMapping("/orders")
    public ResponseEntity<CommerceOrder> place(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody CartRequest cart) {
        PlacementResult result = cart.deliveryMethod() == null && cart.paymentMethod() == null
                ? commerce.placeOrder(CurrentUser.require(), cart.toItems(), idempotencyKey, cart.toShipping())
                : commerce.placeOrder(CurrentUser.require(), cart.toItems(), idempotencyKey, cart.toShipping(),
                        DeliveryMethod.parse(cart.deliveryMethod()), PaymentMethod.parse(cart.paymentMethod()));
        if (!result.created()) return ResponseEntity.ok().header("Idempotent-Replay", "true").body(result.order());
        return ResponseEntity.created(URI.create("/api/shop/orders/" + result.order().id())).body(result.order());
    }

    @GetMapping("/orders")
    public List<CommerceOrder> mine() { return commerce.myOrders(CurrentUser.require()); }

    @GetMapping("/orders/{id}")
    public OrderDetails details(@PathVariable UUID id) { return commerce.myOrder(CurrentUser.require(), id); }

    @PostMapping("/orders/{id}/cancel")
    public CommerceOrder cancel(@PathVariable UUID id) { return commerce.cancelMyOrder(CurrentUser.require(), id); }

    // ---- v6: saved cart -----------------------------------------------------------------------
    @GetMapping("/cart")
    public CartResponse cart() { return CartResponse.of(carts.cart(CurrentUser.require())); }

    @PostMapping("/cart/items")
    public CartResponse add(@RequestBody Item item) {
        return CartResponse.of(carts.addItem(CurrentUser.require(), required(item.productId(), "productId"),
                item.quantity() == null ? 1 : item.quantity()));
    }

    @PutMapping("/cart/items/{productId}")
    public CartResponse set(@PathVariable long productId, @RequestBody Item item) {
        return CartResponse.of(carts.setItem(CurrentUser.require(), productId, required(item.quantity(), "quantity")));
    }

    @DeleteMapping("/cart/items/{productId}")
    public CartResponse remove(@PathVariable long productId) { return CartResponse.of(carts.removeItem(CurrentUser.require(), productId)); }

    @DeleteMapping("/cart")
    public CartResponse clear() { return CartResponse.of(carts.clear(CurrentUser.require())); }

    /** Adds the guest cart (kept in the browser) to the saved cart after signing in. */
    @PostMapping("/cart/merge")
    public CartResponse merge(@RequestBody CartRequest cart) { return CartResponse.of(carts.merge(CurrentUser.require(), cart.toItems())); }

    private static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + ": obligatorio");
        return value;
    }

    public record CartRequest(List<Item> items, Shipping shipping, String deliveryMethod, String paymentMethod) {
        com.polymer.domain.fulfillment.ShippingAddress toShipping() {
            if (shipping == null) return null;
            return new com.polymer.domain.fulfillment.ShippingAddress(shipping.recipient(), shipping.address(), shipping.city(),
                    shipping.phone(), shipping.instructions());
        }

        List<CartItem> toItems() {
            if (items == null) return List.of();
            return items.stream().map(item -> new CartItem(item.productId() == null ? 0 : item.productId(),
                    item.quantity() == null ? 0 : item.quantity())).toList();
        }
    }

    public record Item(Long productId, Integer quantity) {}
    public record Shipping(String recipient, String address, String city, String phone, String instructions) {}

    public record CartResponse(List<Line> lines, int units, BigDecimal subtotal, BigDecimal savings, List<String> problems, String currency) {
        static CartResponse of(CartUseCases.CartView view) {
            return new CartResponse(view.lines().stream().map(l -> new Line(ProductView.of(l.product()), l.quantity(), l.lineTotal(),
                    l.purchasable())).toList(), view.units(), view.subtotal(), view.savings(), view.problems(), "PEN");
        }

        public record Line(ProductView product, int quantity, BigDecimal lineTotal, boolean purchasable) {}
    }

    public record SummaryResponse(List<OrderLine> lines, int units, BigDecimal subtotal, BigDecimal savings, BigDecimal shippingCost,
                                  BigDecimal total, Option delivery, Option payment, String currency) {
        static SummaryResponse of(CheckoutSummary s) {
            DeliveryMethod d = s.delivery();
            PaymentMethod p = s.payment();
            return new SummaryResponse(s.quote().lines(), s.quote().units(), s.subtotal(), s.savings(), s.shippingCost(), s.total(),
                    new Option(d.name(), d.label(), d.eta()), p == null ? null : new Option(p.name(), p.label(), p.instructions()), "PEN");
        }
    }

    public record Option(String code, String label, String detail) {}

    public record CheckoutOptions(List<DeliveryOption> delivery, List<PaymentOption> payment, String currency) {
        static CheckoutOptions current() {
            return new CheckoutOptions(Arrays.stream(DeliveryMethod.values())
                    .map(d -> new DeliveryOption(d.name(), d.label(), d.eta(), d.baseCost(), d.freeFrom(), d.needsAddress())).toList(),
                    Arrays.stream(PaymentMethod.values()).map(p -> new PaymentOption(p.name(), p.label(), p.instructions(),
                            Arrays.stream(DeliveryMethod.values()).filter(p::allowedWith).map(Enum::name).toList(), p.online())).toList(), "PEN");
        }
    }

    public record DeliveryOption(String code, String label, String eta, BigDecimal cost, BigDecimal freeFrom, boolean needsAddress) {}
    public record PaymentOption(String code, String label, String instructions, List<String> deliveries, boolean online) {}
}
