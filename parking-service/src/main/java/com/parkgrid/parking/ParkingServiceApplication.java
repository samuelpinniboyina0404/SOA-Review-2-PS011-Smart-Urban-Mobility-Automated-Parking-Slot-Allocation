package com.parkgrid.parking;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;

@SpringBootApplication
@EnableDiscoveryClient
@EntityScan(basePackages = "com.parkgrid.parking.entity")
@EnableJpaRepositories(basePackages = "com.parkgrid.parking.repository")
public class ParkingServiceApplication {

    @Value("${server.port:8082}")
    private String serverPort;

    public static void main(String[] args) {
        SpringApplication.run(ParkingServiceApplication.class, args);
    }

    @Bean
    public Filter instanceHeaderFilter() {
        return (request, response, chain) -> {
            if (response instanceof HttpServletResponse httpServletResponse) {
                httpServletResponse.setHeader("X-Handled-By", "parking-service:" + serverPort);
            }
            chain.doFilter(request, response);
        };
    }
}
