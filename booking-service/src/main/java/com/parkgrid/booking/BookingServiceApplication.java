package com.parkgrid.booking;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EntityScan(basePackages = "com.parkgrid.booking.entity")
@EnableJpaRepositories(basePackages = "com.parkgrid.booking.repository")
public class BookingServiceApplication {

    @Value("${server.port:8083}")
    private String serverPort;

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public Filter instanceHeaderFilter() {
        return (request, response, chain) -> {
            if (response instanceof HttpServletResponse httpServletResponse) {
                httpServletResponse.setHeader("X-Handled-By", "booking-service:" + serverPort);
            }
            chain.doFilter(request, response);
        };
    }
}
