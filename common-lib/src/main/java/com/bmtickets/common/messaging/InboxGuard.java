package com.bmtickets.common.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer. Must run inside the consumer's business transaction: the marker insert and the
 * business update commit (or roll back) together. A concurrent duplicate hits the primary key and rolls back.
 */
@Component
public class InboxGuard {

    private final ProcessedEventRepository repository;

    public InboxGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /** @return true if this is the first time {@code consumer} sees {@code eventId}; false for a duplicate delivery. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String consumer, String eventId) {
        String id = consumer + ":" + eventId;
        if (repository.existsById(id)) {
            return false;
        }
        repository.saveAndFlush(new ProcessedEvent(consumer, eventId));
        return true;
    }
}
