package com.ecommerce.product_service.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign client configuration that adds required headers for internal service calls.
 * Propagates user context and adds gateway secret for service-to-service authentication.
 */
@Component
public class FeignClientConfig implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();

        // Add gateway secret for internal service authentication
        template.header(GATEWAY_SECRET_HEADER, gatewaySecret);

        // Propagate user context headers from incoming request
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isEmpty()) {
            template.header(USER_ID_HEADER, userId);
        }

        String userRoles = request.getHeader(USER_ROLES_HEADER);
        if (userRoles != null && !userRoles.isEmpty()) {
            template.header(USER_ROLES_HEADER, userRoles);
        }

        // Also propagate authorization if present
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && !authorization.isEmpty()) {
            template.header(AUTHORIZATION_HEADER, authorization);
        }
    }
}
