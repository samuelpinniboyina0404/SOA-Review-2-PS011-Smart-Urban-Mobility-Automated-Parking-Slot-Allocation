package com.parkgrid.billing.controller;

import com.parkgrid.billing.dto.BillingCalculationRequest;
import com.parkgrid.billing.dto.PaymentRequest;
import com.parkgrid.billing.entity.Invoice;
import com.parkgrid.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    @PostMapping("/calculate-and-invoice")
    public ResponseEntity<?> calculateAndInvoice(@RequestBody BillingCalculationRequest request) {
        try {
            Invoice invoice = billingService.calculateAndGenerateInvoice(request);
            return ResponseEntity.ok(invoice);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoices/my")
    public ResponseEntity<List<Invoice>> getMyInvoices(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(required = false) Long userId) {
        Long targetUserId = (headerUserId != null && !headerUserId.isBlank()) 
                ? Long.parseLong(headerUserId) 
                : (userId != null ? userId : 2L);
        return ResponseEntity.ok(billingService.getInvoicesByUserId(targetUserId));
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getInvoiceById(id));
    }

    @PostMapping("/invoices/{id}/pay")
    public ResponseEntity<?> payInvoice(@PathVariable Long id, @RequestBody(required = false) PaymentRequest request) {
        try {
            if (request == null) request = new PaymentRequest("CARD", null);
            Invoice paid = billingService.processPayment(id, request);
            return ResponseEntity.ok(paid);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(billingService.getAllInvoices());
    }

    @PostMapping("/invoices")
    public ResponseEntity<?> createInvoice(@RequestBody Invoice invoice) {
        try {
            Invoice created = billingService.createInvoice(invoice);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/invoices/{id}")
    public ResponseEntity<?> updateInvoice(@PathVariable Long id, @RequestBody Invoice updateDetails) {
        try {
            Invoice updated = billingService.updateInvoice(id, updateDetails);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/invoices/{id}")
    public ResponseEntity<?> deleteInvoice(@PathVariable Long id) {
        try {
            billingService.deleteInvoice(id);
            return ResponseEntity.ok(Map.of("message", "Invoice deleted successfully", "id", id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueAnalytics() {
        return ResponseEntity.ok(billingService.getRevenueAnalytics());
    }

    @GetMapping("/instance-info")
    public ResponseEntity<Map<String, Object>> getInstanceInfo(
            @org.springframework.beans.factory.annotation.Value("${server.port:8084}") String port) {
        return ResponseEntity.ok(Map.of(
                "service", "billing-service",
                "port", port,
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
