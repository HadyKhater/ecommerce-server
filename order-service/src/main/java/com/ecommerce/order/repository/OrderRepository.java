package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoffDate);
    
    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :status AND o.createdAt < :cutoffDate")
    List<Order> findByStatusAndCreatedAtBeforeWithItems(@Param("status") OrderStatus status, @Param("cutoffDate") LocalDateTime cutoffDate);
}
