package com.stayease.repository;

import com.stayease.model.PaymentStatus;
import com.stayease.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PaymentTransaction}.
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTransactionReference(String transactionReference);

    Optional<PaymentTransaction> findByGatewayReference(String gatewayReference);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    Optional<PaymentTransaction> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(Long bookingId, PaymentStatus status);

    @Query("SELECT pt FROM PaymentTransaction pt JOIN FETCH pt.booking b JOIN FETCH b.user WHERE pt.id = :id")
    Optional<PaymentTransaction> findByIdWithBookingAndUser(@Param("id") Long id);

    long countByStatus(PaymentStatus status);

    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
