package com.stayease.controller;

import com.stayease.model.InAppNotification;
import com.stayease.service.InAppNotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final InAppNotificationService notificationService;

    public NotificationApiController(InAppNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Get recent notifications and unread badge count for the current user.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(
            Authentication authentication,
            @RequestParam(name = "limit", defaultValue = "15") int limit) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of("unreadCount", 0, "notifications", List.of()));
        }

        long unreadCount = notificationService.getUnreadCount(authentication);
        List<InAppNotification> notifications = notificationService.getRecentNotifications(authentication, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", unreadCount);
        response.put("notifications", notifications);
        return ResponseEntity.ok(response);
    }

    /**
     * Poll for any new unread notifications since a timestamp (for toast popups).
     */
    @GetMapping("/poll")
    public ResponseEntity<Map<String, Object>> pollUnreadNotifications(
            Authentication authentication,
            @RequestParam(name = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of("unreadCount", 0, "newNotifications", List.of()));
        }

        LocalDateTime sinceTime = since != null ? since : LocalDateTime.now().minusSeconds(15);
        List<InAppNotification> newNotifications = notificationService.getUnreadSince(authentication, sinceTime);
        long unreadCount = notificationService.getUnreadCount(authentication);

        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", unreadCount);
        response.put("newNotifications", newNotifications);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark a single notification as read.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable("id") Long id,
            Authentication authentication) {

        notificationService.markAsRead(id, authentication);
        long unreadCount = notificationService.getUnreadCount(authentication);
        return ResponseEntity.ok(Map.of("status", "success", "unreadCount", unreadCount));
    }

    /**
     * Mark all notifications as read for current user.
     */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication);
        return ResponseEntity.ok(Map.of("status", "success", "unreadCount", 0));
    }
}
