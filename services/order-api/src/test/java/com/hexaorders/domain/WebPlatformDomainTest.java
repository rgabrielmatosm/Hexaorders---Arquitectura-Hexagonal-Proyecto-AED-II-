package com.hexaorders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexaorders.domain.contact.ContactMessage;
import com.hexaorders.domain.fulfillment.ShippingAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebPlatformDomainTest {
    @Test
    void shippingAddressIsTrimmedAndOptionalFieldsBecomeNull() {
        var address = new ShippingAddress("  Ana Pérez ", "Av. Arequipa 1234", "Lima", " ", "");
        assertThat(address.recipient()).isEqualTo("Ana Pérez");
        assertThat(address.phone()).isNull();
        assertThat(address.instructions()).isNull();
    }

    @Test
    void shippingAddressRejectsMissingOrMalformedData() {
        assertThatThrownBy(() -> new ShippingAddress("A", "Av. Arequipa 1234", "Lima", null, null)).hasMessageContaining("recipient");
        assertThatThrownBy(() -> new ShippingAddress("Ana", "Av", "Lima", null, null)).hasMessageContaining("address");
        assertThatThrownBy(() -> new ShippingAddress("Ana", "Av. Arequipa 1234", "", null, null)).hasMessageContaining("city");
        assertThatThrownBy(() -> new ShippingAddress("Ana", "Av. Arequipa 1234", "Lima", "<script>", null)).hasMessageContaining("phone");
        assertThatThrownBy(() -> new ShippingAddress("Ana", "Av. Arequipa 1234", "Lima", null, "x".repeat(301))).hasMessageContaining("instructions");
    }

    @Test
    void contactMessageValidatesEmailTopicAndLength() {
        var ok = new ContactMessage(0, "Ana", "ana@example.com", "DEMO", "Quiero ver una demostración.", Instant.EPOCH);
        assertThat(ok.topic()).isEqualTo("DEMO");
        assertThatThrownBy(() -> new ContactMessage(0, "Ana", "ana-at-example", "DEMO", "Mensaje suficientemente largo", Instant.EPOCH))
                .hasMessageContaining("email");
        assertThatThrownBy(() -> new ContactMessage(0, "Ana", "ana@example.com", "SPAM", "Mensaje suficientemente largo", Instant.EPOCH))
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> new ContactMessage(0, "Ana", "ana@example.com", "OTHER", "corto", Instant.EPOCH))
                .hasMessageContaining("message");
        // POLYMER: el Libro de reclamaciones usa el tema CLAIM (migración V8).
        assertThat(new ContactMessage(0, "Ana", "ana@example.com", "CLAIM", "Tipo: RECLAMO. El producto llegó dañado.", Instant.EPOCH).topic())
                .isEqualTo("CLAIM");
    }
}
