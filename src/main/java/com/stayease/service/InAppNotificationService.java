package com.stayease.service;

import com.stayease.model.InAppNotification;
import com.stayease.repository.InAppNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class InAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationService.class);

    private final InAppNotificationRepository notificationRepository;

    public InAppNotificationService(InAppNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Send notification to a specific user by email.
     */
    @Transactional
    public InAppNotification notifyUser(String email, String title, String message, String type, String linkUrl) {
        InAppNotification notification = new InAppNotification(email, null, title, message, type, linkUrl);
        InAppNotification saved = notificationRepository.save(notification);
        log.info("InAppNotification created for user={}: [{}] {}", email, type, title);
        return saved;
    }

    /**
     * Asynchronously sends notification to a specific user without blocking caller.
     */
    @org.springframework.scheduling.annotation.Async("taskExecutor")
    public void notifyUserAsync(String email, String title, String message, String type, String linkUrl) {
        notifyUser(email, title, message, type, linkUrl);
    }

    /**
     * Send broadcast notification to all admins.
     */
    @Transactional
    public InAppNotification notifyAdmin(String title, String message, String type, String linkUrl) {
        InAppNotification notification = new InAppNotification(null, "ROLE_ADMIN", title, message, type, linkUrl);
        InAppNotification saved = notificationRepository.save(notification);
        log.info("InAppNotification created for admins: [{}] {}", type, title);
        return saved;
    }

    /**
     * Asynchronously sends broadcast notification to all admins without blocking caller.
     */
    @org.springframework.scheduling.annotation.Async("taskExecutor")
    public void notifyAdminAsync(String title, String message, String type, String linkUrl) {
        notifyAdmin(title, message, type, linkUrl);
    }

    /**
     * Get recent notifications (up to limit) for the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public List<InAppNotification> getRecentNotifications(Authentication authentication, int limit) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Collections.emptyList();
        }
        String email = authentication.getName();
        String role = resolveTargetRole(authentication.getAuthorities());
        return notificationRepository.findRecentForUser(email, role, PageRequest.of(0, Math.min(limit, 50)));
    }

    /**
     * Get count of unread notifications for the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return 0;
        }
        String email = authentication.getName();
        String role = resolveTargetRole(authentication.getAuthorities());
        return notificationRepository.countUnreadForUser(email, role);
    }

    /**
     * Get unread notifications since a timestamp for real-time polling / toast popups.
     */
    @Transactional(readOnly = true)
    public List<InAppNotification> getUnreadSince(Authentication authentication, LocalDateTime since) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Collections.emptyList();
        }
        String email = authentication.getName();
        String role = resolveTargetRole(authentication.getAuthorities());
        return notificationRepository.findUnreadSince(email, role, since);
    }

    /**
     * Mark a single notification as read if it belongs to user/role.
     */
    @Transactional
    public void markAsRead(Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        String email = authentication.getName();
        String role = resolveTargetRole(authentication.getAuthorities());
        notificationRepository.markReadForUser(id, email, role);
    }

    /**
     * Mark all notifications as read for current user.
     */
    @Transactional
    public void markAllAsRead(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        String email = authentication.getName();
        String role = resolveTargetRole(authentication.getAuthorities());
        notificationRepository.markAllReadForUser(email, role);
    }

    private String resolveTargetRole(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) return null;
        for (GrantedAuthority ga : authorities) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                return "ROLE_ADMIN";
            }
        }
        return null;
    }
}
