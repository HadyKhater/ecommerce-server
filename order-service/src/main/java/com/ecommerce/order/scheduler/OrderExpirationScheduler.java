package com.ecommerce.order.scheduler;

import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.client.NotificationClient;
import com.ecommerce.order.dto.ReserveStockRequest;
import com.ecommerce.order.dto.SendNotificationRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final NotificationClient notificationClient;

    /**
     * Runs every 10 minutes to check for expired reservations.
     * Finds all RESERVED orders created more than 30 minutes ago and expires them.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void expireReservations() {
        log.info("Starting scheduled task to check for expired order reservations");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusMinutes(2);
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBeforeWithItems(OrderStatus.RESERVED, cutoffDate);
        
        if (expiredOrders.isEmpty()) {
            log.debug("No expired reservations found");
            return;
        }
        
        log.info("Found {} order(s) with expired reservations", expiredOrders.size());
        
        for (Order order : expiredOrders) {
            try {
                expireOrder(order);
            } catch (Exception e) {
                log.error("Failed to expire order {}: {}. Continuing with next order.", 
                         order.getId(), e.getMessage());
            }
        }
        
        log.info("Completed processing expired reservations. Processed {} order(s)", expiredOrders.size());
    }
    
    @Transactional
    private void expireOrder(Order order) {
        log.info("Expiring order {} - reservation timed out after 30 minutes", order.getId());
        
        // Update order status to EXPIRED
        order.setStatus(OrderStatus.EXPIRED);
        orderRepository.save(order);
        
        // Release stock for all items in the order
        boolean allReleasesSucceeded = releaseStock(order);
        
        if (allReleasesSucceeded) {
            log.info("Successfully expired order {} and released stock", order.getId());
        } else {
            log.error("Expired order {} but failed to release stock for some items. Order status updated to EXPIRED.", 
                     order.getId());
        }

        // Notify user about expired reservation
        try {
            log.info("Sending reservation expired notification for order {} to user {}", order.getId(), order.getUserId());
            notificationClient.sendNotification(new SendNotificationRequest(
                    order.getUserId(),
                    "Order Reservation Expired",
                    "Your order reservation has expired",
                    "ORDER"
            ));
        } catch (Exception e) {
            log.error("Failed to send reservation expired notification for order {} (non-critical): {}", 
                     order.getId(), e.getMessage());
        }
    }
    
    /**
     * Attempts to release stock for all items in the order.
     * @param order The order to release stock for
     * @return true if all releases succeeded, false if any failed
     */
    private boolean releaseStock(Order order) {
        boolean allSucceeded = true;
        int failedCount = 0;
        
        for (var item : order.getItems()) {
            try {
                inventoryClient.releaseStock(new ReserveStockRequest(item.getProductId(), item.getQuantity()));
                log.debug("Released stock for product {} in order {}", 
                         item.getProductId(), order.getId());
            } catch (Exception e) {
                log.error("Failed to release stock for product {} in order {}: {}", 
                         item.getProductId(), order.getId(), e.getMessage());
                allSucceeded = false;
                failedCount++;
                // Continue with other items even if one fails
            }
        }
        
        if (!allSucceeded) {
            log.warn("Stock release completed with {} failure(s) for order {}", 
                    failedCount, order.getId());
        }
        
        return allSucceeded;
    }
}