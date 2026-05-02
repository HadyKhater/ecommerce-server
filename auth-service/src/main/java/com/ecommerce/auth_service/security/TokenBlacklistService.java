package com.ecommerce.auth_service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TokenBlacklistService {

    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    public void blacklistToken(String token) {
        blacklistedTokens.add(token);
        log.info("Token blacklisted. Total blacklisted tokens: {}", blacklistedTokens.size());
    }

    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }

    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }

    public void clearBlacklist() {
        blacklistedTokens.clear();
        log.info("Token blacklist cleared");
    }
}
