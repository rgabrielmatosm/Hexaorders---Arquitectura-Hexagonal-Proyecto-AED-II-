package com.hexaorders.adapter.in.web;

import com.hexaorders.adapter.in.web.security.CurrentUser;
import com.hexaorders.application.port.in.CommerceUseCases;
import com.hexaorders.application.port.in.OrderDetails;
import com.hexaorders.application.port.in.PlacementResult;
import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.Quote;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer portal: server-side pricing, checkout (idempotent), own orders and cancellation. */
@RestController
@RequestMapping("/api/shop")
public final class ShopController {
    private final CommerceUseCases commerce;

    public ShopController(CommerceUseCases commerce) { this.commerce = commerce; }

    @PostMapping("/quote")
    public Quote quote(@RequestBody CartRequest cart) { return commerce.quote(cart.toItems()); }

    @PostMapping("/orders")
    public ResponseEntity<CommerceOrder> place(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody CartRequest cart) {
        PlacementResult result = commerce.placeOrder(CurrentUser.require(), cart.toItems(), idempotencyKey);
        if (!result.created()) return ResponseEntity.ok().header("Idempotent-Replay", "true").body(result.order());
        return ResponseEntity.created(URI.create("/api/shop/orders/" + result.order().id())).body(result.order());
    }

    @GetMapping("/orders")
    public List<CommerceOrder> mine() { return commerce.myOrders(CurrentUser.require()); }

    @GetMapping("/orders/{id}")
    public OrderDetails details(@PathVariable UUID id) { return commerce.myOrder(CurrentUser.require(), id); }

    @PostMapping("/orders/{id}/cancel")
    public CommerceOrder cancel(@PathVariable UUID id) { return commerce.cancelMyOrder(CurrentUser.require(), id); }

    public record CartRequest(List<Item> items) {
        List<CartItem> toItems() {
            if (items == null) return List.of();
            return items.stream().map(item -> new CartItem(item.productId() == null ? 0 : item.productId(),
                    item.quantity() == null ? 0 : item.quantity())).toList();
        }
    }

    public record Item(Long productId, Integer quantity) {}
}
