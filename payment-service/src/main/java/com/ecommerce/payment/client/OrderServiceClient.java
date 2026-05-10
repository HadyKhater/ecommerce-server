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
    }
