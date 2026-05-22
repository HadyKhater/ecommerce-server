package com.ecommerce.notification_service.controller;

import com.ecommerce.notification_service.dto.NotificationResponse;
import com.ecommerce.notification_service.dto.SendNotificationRequest;
import com.ecommerce.notification_service.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null || authentication.getPrincipal().equals("anonymousUser")) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        try {
            return UUID.fromString(principal.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUserOrAdmin(UUID userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
        UUID currentUserId = getCurrentUserId();
        return isAdmin || (currentUserId != null && currentUserId.equals(userId));
    }

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody SendNotificationRequest request) {
        log.info("POST /notifications/send - Internal call to send notification");
        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<NotificationResponse>> getNotificationsForUser(@PathVariable UUID userId) {
        log.info("GET /notifications/user/{} - Get top 20 notifications", userId);
        
        if (!isUserOrAdmin(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<NotificationResponse> responses = notificationService.getNotificationsForUser(userId);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable UUID id) {
        log.info("PUT /notifications/{}/read - Mark notification as read", id);
        
        // Since we need to mark it read, let's execute the service call
        // The service throws IllegalArgumentException if not found
        try {
            NotificationResponse response = notificationService.markAsRead(id);
            // Verify access control
            if (!isUserOrAdmin(response.userId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{userId}/unread-count")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Long> getUnreadCount(@PathVariable UUID userId) {
        log.info("GET /notifications/user/{}/unread-count - Get unread count", userId);
        
        if (!isUserOrAdmin(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }
}
