package com.stayease.repository;

import com.stayease.model.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    Page<AdminAuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query(value = "SELECT l FROM AdminAuditLog l " +
           "WHERE (:adminEmail IS NULL OR LOWER(l.adminEmail) LIKE LOWER(CONCAT('%', :adminEmail, '%'))) " +
           "AND (:action IS NULL OR l.action = :action) " +
           "AND (:start IS NULL OR l.timestamp >= :start) " +
           "AND (:end IS NULL OR l.timestamp <= :end) " +
           "ORDER BY l.timestamp DESC",
           countQuery = "SELECT count(l) FROM AdminAuditLog l " +
           "WHERE (:adminEmail IS NULL OR LOWER(l.adminEmail) LIKE LOWER(CONCAT('%', :adminEmail, '%'))) " +
           "AND (:action IS NULL OR l.action = :action) " +
           "AND (:start IS NULL OR l.timestamp >= :start) " +
           "AND (:end IS NULL OR l.timestamp <= :end)")
    Page<AdminAuditLog> searchAuditLogs(
            @Param("adminEmail") String adminEmail,
            @Param("action") String action,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
}
