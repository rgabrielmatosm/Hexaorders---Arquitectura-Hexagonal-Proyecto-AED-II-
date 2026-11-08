package com.polymer.application.port.in;

import com.polymer.domain.catalog.PricedProduct;
import com.polymer.domain.fulfillment.CartItem;
import java.math.BigDecimal;
import java.util.List;

/** Persistent cart of a signed-in customer. Prices and stock always come from the catalog. */
public interface CartUseCases {
    CartView cart(AuthenticatedUser customer);
    CartView setItem(AuthenticatedUser customer, long productId, int quantity);
    CartView addItem(AuthenticatedUser customer, long productId, int quantity);
    CartView removeItem(AuthenticatedUser customer, long productId);
    CartView clear(AuthenticatedUser customer);
    /** Adds the items of a guest cart (browser) to the saved cart after signing in. */
    CartView merge(AuthenticatedUser customer, List<CartItem> items);

    /**
     * lines: priced lines; problems: lines that cannot be bought as they are (out of stock, not enough
     * units, product no longer sold) so the customer can fix them before checkout.
     */
    record CartView(List<CartLine> lines, int units, BigDecimal subtotal, BigDecimal savings, List<String> problems) {}

    record CartLine(PricedProduct product, int quantity, BigDecimal lineTotal, boolean purchasable) {}
}
