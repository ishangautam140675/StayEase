package com.stayease.repository;

import com.stayease.model.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PaymentWebhookEvent}.
 */
@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
