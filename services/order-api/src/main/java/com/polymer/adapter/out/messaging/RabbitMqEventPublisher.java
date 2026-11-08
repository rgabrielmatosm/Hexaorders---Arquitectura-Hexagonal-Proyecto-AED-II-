package com.polymer.adapter.out.messaging;

import com.polymer.adapter.messaging.EventContract;
import com.polymer.application.exception.EventPublishingException;
import com.polymer.application.port.out.EventPublisher;
import com.polymer.domain.event.BusinessEvent;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ adapter with publisher confirms and mandatory routing: publish() returns only when the
 * broker acknowledged a routable, persistent message; otherwise it throws so the outbox keeps it.
 */
public final class RabbitMqEventPublisher implements EventPublisher {
    private final RabbitTemplate rabbit;
    private final long confirmTimeoutMillis;

    public RabbitMqEventPublisher(RabbitTemplate rabbit, long confirmTimeoutMillis) {
        this.rabbit = rabbit;
        this.confirmTimeoutMillis = confirmTimeoutMillis;
    }

    @Override
    public void publish(BusinessEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId());
        properties.setType(event.type().name());
        properties.setContentType(EventContract.CONTENT_TYPE);
        properties.setContentEncoding("UTF-8");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader("schemaVersion", event.schemaVersion());
        properties.setHeader("orderId", event.orderId().toString());
        CorrelationData correlation = new CorrelationData(event.eventId());
        try {
            rabbit.send(EventContract.EXCHANGE, event.type().routingKey(), new Message(EventContract.encode(event), properties), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) throw new EventPublishingException("El broker rechazó el evento " + event.eventId() + ": " + confirm.getReason(), null);
            if (correlation.getReturned() != null) throw new EventPublishingException("Evento sin cola de destino: " + event.eventId(), null);
        } catch (EventPublishingException rejected) {
            throw rejected;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException("Publicación interrumpida", interrupted);
        } catch (Exception unavailable) {
            throw new EventPublishingException("RabbitMQ no disponible: " + unavailable.getMessage(), unavailable);
        }
    }
}
