package com.ecommerce.product_service.config;

import com.ecommerce.product_service.security.JwtAuthenticationFilter;
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
                        .requestMatchers(HttpMethod.GET, "/products", "/products/all", "/products/{id}",
                                "/products/*/images")
                        .hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/categories", "/categories/{id}", "/categories/slug/{slug}")
                        .hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/categories/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/categories/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/categories/*/toggle-active").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/products/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/products/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/banners/public").permitAll()
                        .requestMatchers(HttpMethod.GET, "/banners").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/banners").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/banners/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/banners/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/banners/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/promotions").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/promotions/active").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/promotions/validate").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/promotions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/promotions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/promotions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/promotions/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
