package com.parkgrid.billing.service;

import com.parkgrid.billing.dto.BillingCalculationRequest;
import com.parkgrid.billing.dto.PaymentRequest;
import com.parkgrid.billing.entity.Invoice;
import com.parkgrid.billing.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.*;

@Service
public class BillingService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    public Invoice calculateAndGenerateInvoice(BillingCalculationRequest request) {
        // Check if invoice already generated for this booking
        Optional<Invoice> existing = invoiceRepository.findByBookingId(request.getBookingId());
        if (existing.isPresent()) {
            return existing.get();
        }

        LocalDateTime start = request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now().minusHours(1);
        LocalDateTime end = request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now();

        long minutes = Math.max(1, Duration.between(start, end).toMinutes());
        double durationHours = Math.ceil((double) minutes / 60.0); // minimum 1-hour billing unit

        // Base rate lookup by vehicle type / zone
        double baseRate = 5.0; // default base rate
        String vType = request.getVehicleType() != null ? request.getVehicleType().toUpperCase() : "CAR";
        
        double vehicleMultiplier = 1.0;
        switch (vType) {
            case "EV":
                vehicleMultiplier = 1.25; // Charging station tariff
                baseRate = 7.50;
                break;
            case "SUV":
                vehicleMultiplier = 1.50; // Large bay tariff
                baseRate = 12.00;
                break;
            case "BIKE":
                vehicleMultiplier = 0.50; // Two-wheeler discount
                baseRate = 4.00;
                break;
            default:
                vehicleMultiplier = 1.00;
                baseRate = 5.00;
                break;
        }

        // Peak Hour Surcharge calculation (08:00 - 11:00 or 17:00 - 20:00)
        int startHour = start.getHour();
        boolean isPeak = (startHour >= 8 && startHour <= 11) || (startHour >= 17 && startHour <= 20);
        double peakMultiplier = isPeak ? 1.25 : 1.00;

        // Overstay penalty fee (if total duration > 4 hours tier)
        double overstayFee = (durationHours > 4.0) ? (durationHours - 4.0) * (baseRate * 0.5) : 0.0;

        double calculatedTotal = (baseRate * durationHours * vehicleMultiplier * peakMultiplier) + overstayFee;
        double roundedTotal = Math.round(calculatedTotal * 100.0) / 100.0;

        String invNum = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Invoice invoice = new Invoice(
                invNum,
                request.getBookingId(),
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getUserEmail() != null ? request.getUserEmail() : "driver@parkgrid.com",
                request.getSlotId(),
                request.getZoneId(),
                vType,
                start,
                end,
                minutes,
                baseRate,
                vehicleMultiplier,
                peakMultiplier,
                overstayFee,
                roundedTotal
        );

        return invoiceRepository.save(invoice);
    }

    public List<Invoice> getInvoicesByUserId(Long userId) {
        return invoiceRepository.findByUserId(userId);
    }

    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with ID: " + id));
    }

    public Invoice processPayment(Long invoiceId, PaymentRequest paymentRequest) {
        Invoice invoice = getInvoiceById(invoiceId);
        if ("PAID".equalsIgnoreCase(invoice.getPaymentStatus())) {
            return invoice;
        }

        invoice.setPaymentStatus("PAID");
        invoice.setPaymentMethod(paymentRequest.getPaymentMethod() != null ? paymentRequest.getPaymentMethod() : "CARD");
        invoice.setPaidAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    public Map<String, Object> getRevenueAnalytics() {
        List<Invoice> invoices = invoiceRepository.findAll();
        double totalRevenue = invoices.stream()
                .filter(i -> "PAID".equalsIgnoreCase(i.getPaymentStatus()))
                .mapToDouble(Invoice::getTotalAmount)
                .sum();

        double totalPending = invoices.stream()
                .filter(i -> "PENDING".equalsIgnoreCase(i.getPaymentStatus()))
                .mapToDouble(Invoice::getTotalAmount)
                .sum();

        long paidCount = invoices.stream().filter(i -> "PAID".equalsIgnoreCase(i.getPaymentStatus())).count();
        double avgTicket = paidCount > 0 ? totalRevenue / paidCount : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        stats.put("pendingRevenue", Math.round(totalPending * 100.0) / 100.0);
        stats.put("totalInvoicesIssued", invoices.size());
        stats.put("paidInvoicesCount", paidCount);
        stats.put("averageTicketValue", Math.round(avgTicket * 100.0) / 100.0);
        return stats;
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public Invoice createInvoice(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (invoice.getBookingId() == null) {
            invoice.setBookingId(0L);
        }
        if (invoice.getUserId() == null) {
            invoice.setUserId(1L);
        }
        if (invoice.getPaymentStatus() == null) {
            invoice.setPaymentStatus("PENDING");
        }
        if (invoice.getTotalAmount() == null) {
            invoice.setTotalAmount(10.0);
        }
        return invoiceRepository.save(invoice);
    }

    public Invoice updateInvoice(Long id, Invoice updateDetails) {
        Invoice invoice = getInvoiceById(id);
        if (updateDetails.getTotalAmount() != null) {
            invoice.setTotalAmount(updateDetails.getTotalAmount());
        }
        if (updateDetails.getPaymentStatus() != null) {
            invoice.setPaymentStatus(updateDetails.getPaymentStatus().toUpperCase());
        }
        if (updateDetails.getPaymentMethod() != null) {
            invoice.setPaymentMethod(updateDetails.getPaymentMethod());
        }
        if (updateDetails.getVehicleType() != null) {
            invoice.setVehicleType(updateDetails.getVehicleType());
        }
        return invoiceRepository.save(invoice);
    }

    public void deleteInvoice(Long id) {
        if (!invoiceRepository.existsById(id)) {
            throw new RuntimeException("Invoice not found with ID: " + id);
        }
        invoiceRepository.deleteById(id);
    }
}
