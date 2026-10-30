package com.parkgrid.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/validate",
            "/h2-console",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Allow CORS preflight requests
        if (request.getMethod().name().equalsIgnoreCase("OPTIONS")) {
            return chain.filter(exchange);
        }

        // Allow public endpoint access without token
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check Authorization header
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return onError(exchange, "Missing Authorization Header", HttpStatus.UNAUTHORIZED);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Invalid Authorization Header Format", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return onError(exchange, "Invalid or Expired JWT Token", HttpStatus.UNAUTHORIZED);
        }

        Claims claims = jwtUtil.extractAllClaims(token);
        String userEmail = claims.getSubject();
        Object userIdObj = claims.get("userId");
        String userId = userIdObj != null ? userIdObj.toString() : "";
        String userRole = claims.get("role", String.class);

        // Enforce Admin Role check on admin paths
        if (isAdminOnlyPath(path, request.getMethod().name()) && !"ROLE_ADMIN".equalsIgnoreCase(userRole)) {
            return onError(exchange, "Access Denied: Requires Admin Privileges", HttpStatus.FORBIDDEN);
        }

        // Forward request with injected user context headers
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", userEmail)
                .header("X-User-Role", userRole != null ? userRole : "ROLE_USER")
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private boolean isPublicPath(String path) {
        if (path.endsWith("/instance-info")) {
            return true;
        }
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isAdminOnlyPath(String path, String method) {
        // Zone modifications (POST, PUT, DELETE)
        if (path.startsWith("/api/parking/zones") && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return true;
        }
        // Slot creation or deletion
        if (path.contains("/slots") && ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return true;
        }
        // User management (Admin only)
        if (path.startsWith("/api/auth/users")) {
            return true;
        }
        // All invoices listing or deletion (Admin only)
        if (path.equals("/api/billing/invoices") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        if (path.startsWith("/api/billing/invoices") && "DELETE".equalsIgnoreCase(method)) {
            return true;
        }
        // Analytics and global bookings view
        if (path.startsWith("/api/parking/analytics/") || path.startsWith("/api/billing/analytics/") || path.equals("/api/bookings/all")) {
            return true;
        }
        return false;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errMessage, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add("Content-Type", "application/json");
        String body = String.format("{\"error\": \"%s\", \"status\": %d}", errMessage, httpStatus.value());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
