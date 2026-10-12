package com.stayease.repository;

import com.stayease.model.InAppNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    @Query("SELECT n FROM InAppNotification n " +
           "WHERE n.recipientEmail = :email OR (n.targetRole IS NOT NULL AND n.targetRole = :role) " +
           "ORDER BY n.createdAt DESC")
    List<InAppNotification> findRecentForUser(@Param("email") String email,
                                              @Param("role") String role,
                                              Pageable pageable);

    @Query("SELECT COUNT(n) FROM InAppNotification n " +
           "WHERE (n.recipientEmail = :email OR (n.targetRole IS NOT NULL AND n.targetRole = :role)) " +
           "AND n.read = false")
    long countUnreadForUser(@Param("email") String email,
                            @Param("role") String role);

    @Query("SELECT n FROM InAppNotification n " +
           "WHERE (n.recipientEmail = :email OR (n.targetRole IS NOT NULL AND n.targetRole = :role)) " +
           "AND n.read = false AND n.createdAt > :since " +
           "ORDER BY n.createdAt ASC")
    List<InAppNotification> findUnreadSince(@Param("email") String email,
                                            @Param("role") String role,
                                            @Param("since") LocalDateTime since);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InAppNotification n SET n.read = true " +
           "WHERE (n.recipientEmail = :email OR (n.targetRole IS NOT NULL AND n.targetRole = :role)) " +
           "AND n.read = false")
    void markAllReadForUser(@Param("email") String email,
                            @Param("role") String role);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InAppNotification n SET n.read = true " +
           "WHERE n.id = :id AND (n.recipientEmail = :email OR (n.targetRole IS NOT NULL AND n.targetRole = :role))")
    void markReadForUser(@Param("id") Long id,
                         @Param("email") String email,
                         @Param("role") String role);

    @Query("SELECT n FROM InAppNotification n " +
           "WHERE n.id = :id AND (n.recipientEmail = :email OR (n.targetRole IS NOT NULL AND n.targetRole = :role))")
    java.util.Optional<InAppNotification> findByIdForUser(@Param("id") Long id,
                                                          @Param("email") String email,
                                                          @Param("role") String role);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM InAppNotification n " +
           "WHERE n.read = true AND (n.recipientEmail = :email OR (n.targetRole IS NOT NULL AND n.targetRole = :role))")
    int deleteAllReadForUser(@Param("email") String email,
                             @Param("role") String role);
}
