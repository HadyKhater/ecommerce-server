package com.ecommerce.payment.client;

import com.ecommerce.payment.config.FeignConfig;
import com.ecommerce.payment.dto.SendNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", configuration = FeignConfig.class)
public interface NotificationClient {

    @PostMapping("/notifications/send")
    void sendNotification(@RequestBody SendNotificationRequest request);
}
