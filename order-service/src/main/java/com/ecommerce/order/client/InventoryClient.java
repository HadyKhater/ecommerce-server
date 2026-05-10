package com.ecommerce.order.client;

import com.ecommerce.order.config.FeignConfig;
import com.ecommerce.order.dto.ReserveStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", configuration = FeignConfig.class)
public interface InventoryClient {

    @PostMapping("/inventory/reserve")
    void reserveStock(@RequestBody ReserveStockRequest request);

    @PostMapping("/inventory/release")
    void releaseStock(@RequestBody ReserveStockRequest request);
}
