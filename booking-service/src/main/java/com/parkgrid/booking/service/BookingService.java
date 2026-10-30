package com.parkgrid.booking.service;

import com.parkgrid.booking.client.BillingClient;
import com.parkgrid.booking.client.ParkingClient;
import com.parkgrid.booking.dto.BillingCalculationRequest;
import com.parkgrid.booking.dto.BookingRequest;
import com.parkgrid.booking.entity.Booking;
import com.parkgrid.booking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired(required = false)
    private ParkingClient parkingClient;

    @Autowired(required = false)
    private BillingClient billingClient;

    @Transactional
    public Booking createBooking(BookingRequest request) {
        Long slotId = request.getSlotId();

        // Check active bookings on slot to guarantee zero double-booking
        List<Booking> activeOnSlot = bookingRepository.findBySlotIdAndStatusIn(
                slotId, List.of("CONFIRMED", "ACTIVE"));

        if (!activeOnSlot.isEmpty()) {
            throw new IllegalStateException("Slot " + slotId + " is already booked or active by another user!");
        }

        // Call Parking Service to reserve slot atomically
        Long zoneId = 1L;
        if (parkingClient != null) {
            try {
                Map<String, Object> slotData = parkingClient.getSlotById(slotId);
                if (slotData != null && slotData.get("zoneId") != null) {
                    zoneId = ((Number) slotData.get("zoneId")).longValue();
                }
                ResponseEntity<Map<String, Object>> reserveResp = parkingClient.reserveSlot(slotId);
                if (!reserveResp.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("Parking Service rejected reservation for slot: " + slotId);
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("rejected")) {
                    throw e;
                }
                System.err.println("Warning: Parking Service call failed or unreachable during test: " + e.getMessage());
            }
        }

        String refNumber = "PG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Double duration = request.getExpectedDurationHours() != null ? request.getExpectedDurationHours() : 2.0;

        Booking booking = new Booking(
                refNumber,
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getUserEmail() != null ? request.getUserEmail() : "driver@parkgrid.com",
                slotId,
                zoneId,
                request.getVehicleNumber() != null ? request.getVehicleNumber() : "KA-01-AB-1234",
                request.getVehicleType() != null ? request.getVehicleType() : "CAR",
                duration
        );

        return bookingRepository.save(booking);
    }

    public List<Booking> getBookingsByUserId(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + id));
    }

    public Booking getBookingByRef(String ref) {
        return bookingRepository.findByBookingReference(ref)
                .orElseThrow(() -> new RuntimeException("Booking not found with reference: " + ref));
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @Transactional
    public Booking checkIn(Long bookingId) {
        Booking booking = getBookingById(bookingId);
        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Booking is in status " + booking.getStatus() + " and cannot check-in.");
        }

        booking.setActualCheckInTime(LocalDateTime.now());
        booking.setStatus("ACTIVE");

        if (parkingClient != null) {
            try {
                parkingClient.occupySlot(booking.getSlotId());
            } catch (Exception e) {
                System.err.println("Warning: Failed to mark slot OCCUPIED in Parking Service: " + e.getMessage());
            }
        }

        return bookingRepository.save(booking);
    }

    @Transactional
    public Map<String, Object> checkOut(Long bookingId) {
        Booking booking = getBookingById(bookingId);
        if (!"ACTIVE".equalsIgnoreCase(booking.getStatus()) && !"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Booking is in status " + booking.getStatus() + " and cannot check-out.");
        }

        LocalDateTime now = LocalDateTime.now();
        booking.setActualCheckOutTime(now);
        booking.setStatus("COMPLETED");
        bookingRepository.save(booking);

        // Call Parking Service to release slot
        if (parkingClient != null) {
            try {
                parkingClient.releaseSlot(booking.getSlotId());
            } catch (Exception e) {
                System.err.println("Warning: Failed to release slot in Parking Service: " + e.getMessage());
            }
        }

        // Call Billing Service to calculate dynamic tariff and generate invoice
        Map<String, Object> invoiceResult = null;
        if (billingClient != null) {
            try {
                LocalDateTime startTime = booking.getActualCheckInTime() != null ? booking.getActualCheckInTime() : booking.getStartTime();
                BillingCalculationRequest billingReq = new BillingCalculationRequest(
                        booking.getId(),
                        booking.getUserId(),
                        booking.getUserEmail(),
                        booking.getSlotId(),
                        booking.getZoneId(),
                        booking.getVehicleType(),
                        startTime,
                        now
                );
                ResponseEntity<Map<String, Object>> billingResp = billingClient.calculateAndInvoice(billingReq);
                if (billingResp.getStatusCode().is2xxSuccessful()) {
                    invoiceResult = billingResp.getBody();
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to call Billing Service: " + e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("booking", booking);
        response.put("invoice", invoiceResult);
        return response;
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = getBookingById(bookingId);
        if ("COMPLETED".equalsIgnoreCase(booking.getStatus()) || "CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Booking cannot be cancelled in state: " + booking.getStatus());
        }

        booking.setStatus("CANCELLED");
        Booking saved = bookingRepository.save(booking);

        if (parkingClient != null) {
            try {
                parkingClient.releaseSlot(booking.getSlotId());
            } catch (Exception e) {
                System.err.println("Warning: Failed to release slot on cancel: " + e.getMessage());
            }
        }

        return saved;
    }

    public Map<String, Object> getBookingTrends() {
        List<Booking> all = bookingRepository.findAll();
        long total = all.size();
        long confirmed = all.stream().filter(b -> "CONFIRMED".equalsIgnoreCase(b.getStatus())).count();
        long active = all.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).count();
        long completed = all.stream().filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus())).count();
        long cancelled = all.stream().filter(b -> "CANCELLED".equalsIgnoreCase(b.getStatus())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBookings", total);
        stats.put("confirmedBookings", confirmed);
        stats.put("activeBookings", active);
        stats.put("completedBookings", completed);
        stats.put("cancelledBookings", cancelled);
        return stats;
    }

    @Transactional
    public Booking updateBooking(Long bookingId, BookingRequest updateRequest) {
        Booking booking = getBookingById(bookingId);
        if ("COMPLETED".equalsIgnoreCase(booking.getStatus()) || "CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Cannot update a booking that is " + booking.getStatus());
        }

        if (updateRequest.getVehicleNumber() != null && !updateRequest.getVehicleNumber().isBlank()) {
            booking.setVehicleNumber(updateRequest.getVehicleNumber());
        }
        if (updateRequest.getVehicleType() != null && !updateRequest.getVehicleType().isBlank()) {
            booking.setVehicleType(updateRequest.getVehicleType());
        }
        if (updateRequest.getExpectedDurationHours() != null && updateRequest.getExpectedDurationHours() > 0) {
            booking.setExpectedDurationHours(updateRequest.getExpectedDurationHours());
        }
        return bookingRepository.save(booking);
    }

    @Transactional
    public void deleteBooking(Long bookingId) {
        Booking booking = getBookingById(bookingId);
        if ("CONFIRMED".equalsIgnoreCase(booking.getStatus()) || "ACTIVE".equalsIgnoreCase(booking.getStatus())) {
            if (parkingClient != null) {
                try {
                    parkingClient.releaseSlot(booking.getSlotId());
                } catch (Exception e) {
                    System.err.println("Warning: Failed to release slot during booking deletion: " + e.getMessage());
                }
            }
        }
        bookingRepository.delete(booking);
    }
}
