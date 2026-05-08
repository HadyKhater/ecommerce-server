package com.ecommerce.inventory_service.config;

import com.ecommerce.inventory_service.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/inventory/{productId}/check").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/inventory/{productId}").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/inventory").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/inventory/low-stock").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/inventory/{productId}/history").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/inventory/restock").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/inventory/{productId}/adjust").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/inventory/reserve").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/inventory/release").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/inventory/initialize/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
