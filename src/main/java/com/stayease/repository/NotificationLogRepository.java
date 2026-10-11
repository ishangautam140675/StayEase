package com.stayease.repository;

import com.stayease.model.NotificationLog;
import com.stayease.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKeyAndStatus(String idempotencyKey, NotificationStatus status);

    List<NotificationLog> findByStatusOrderByCreatedAtAsc(NotificationStatus status);
}
