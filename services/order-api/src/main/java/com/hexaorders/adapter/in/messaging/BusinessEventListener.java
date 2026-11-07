package com.hexaorders.adapter.in.messaging;

import com.hexaorders.adapter.messaging.EventContract;
import com.hexaorders.application.port.in.NotificationUseCases;
import com.hexaorders.domain.event.BusinessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Driving adapter: RabbitMQ → NotificationUseCases. A malformed or unsupported message goes
 * straight to the dead-letter queue; a failure while notifying is retried (listener retry policy)
 * and, if it keeps failing, dead-lettered as well. Messages are acknowledged after handling.
 */
public final class BusinessEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(BusinessEventListener.class);
    private final NotificationUseCases notifications;

    public BusinessEventListener(NotificationUseCases notifications) { this.notifications = notifications; }

    @RabbitListener(queues = EventContract.NOTIFICATIONS_QUEUE, id = "notifications")
    public void onEvent(Message message) {
        BusinessEvent event;
        try {
            event = EventContract.decode(message.getBody());
        } catch (IllegalArgumentException invalid) {
            LOG.warn("Evento descartado a la DLQ: {}", invalid.getMessage());
            throw new AmqpRejectAndDontRequeueException(invalid.getMessage(), invalid);
        }
        String result = notifications.handle(event);
        LOG.info("Evento {} ({}) procesado: {}", event.eventId(), event.type(), result);
    }
}
