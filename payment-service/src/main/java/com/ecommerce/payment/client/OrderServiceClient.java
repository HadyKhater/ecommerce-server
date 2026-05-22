    package com.ecommerce.payment.client;

    import org.springframework.cloud.openfeign.FeignClient;
    import org.springframework.web.bind.annotation.PathVariable;
    import org.springframework.web.bind.annotation.PutMapping;
    import org.springframework.web.bind.annotation.RequestParam;

    import java.util.UUID;

    import com.ecommerce.payment.config.FeignConfig;

    @FeignClient(name = "order-service", configuration = FeignConfig.class)
    public interface OrderServiceClient {

        @PutMapping("/orders/{orderId}/payment-status")
        void updateOrderPaymentStatus(@PathVariable("orderId") UUID orderId, @RequestParam("status") String status);
        
        /**
         * Confirm a reserved order after successful payment (two-phase commit)
         */
        @PutMapping("/orders/{orderId}/confirm")
        void confirmOrder(@PathVariable("orderId") UUID orderId);
        
        /**
         * Cancel a reserved order on payment failure (two-phase commit)
         */
        @PutMapping("/orders/{orderId}/cancel-reservation")
        void cancelReservation(@PathVariable("orderId") UUID orderId);
    }
