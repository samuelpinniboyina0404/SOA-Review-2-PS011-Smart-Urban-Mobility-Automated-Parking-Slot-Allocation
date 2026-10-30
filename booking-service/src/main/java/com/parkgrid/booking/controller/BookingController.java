package com.parkgrid.booking.controller;

import com.parkgrid.booking.dto.BookingRequest;
import com.parkgrid.booking.entity.Booking;
import com.parkgrid.booking.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @PostMapping
    public ResponseEntity<?> createBooking(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @RequestBody BookingRequest request) {
        try {
            if (headerUserId != null && !headerUserId.isBlank()) {
                request.setUserId(Long.parseLong(headerUserId));
            }
            if (headerUserEmail != null && !headerUserEmail.isBlank()) {
                request.setUserEmail(headerUserEmail);
            }
            Booking booking = bookingService.createBooking(request);
            return ResponseEntity.ok(booking);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<Booking>> getMyBookings(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(required = false) Long userId) {
        Long targetUserId = (headerUserId != null && !headerUserId.isBlank()) 
                ? Long.parseLong(headerUserId) 
                : (userId != null ? userId : 2L);
        return ResponseEntity.ok(bookingService.getBookingsByUserId(targetUserId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @GetMapping("/ref/{ref}")
    public ResponseEntity<Booking> getBookingByRef(@PathVariable String ref) {
        return ResponseEntity.ok(bookingService.getBookingByRef(ref));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<?> checkIn(@PathVariable Long id) {
        try {
            Booking active = bookingService.checkIn(id);
            return ResponseEntity.ok(active);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<?> checkOut(@PathVariable Long id) {
        try {
            Map<String, Object> result = bookingService.checkOut(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        try {
            Booking cancelled = bookingService.cancelBooking(id);
            return ResponseEntity.ok(cancelled);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBooking(@PathVariable Long id, @RequestBody BookingRequest request) {
        try {
            Booking updated = bookingService.updateBooking(id, request);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBooking(@PathVariable Long id) {
        try {
            bookingService.deleteBooking(id);
            return ResponseEntity.ok(Map.of("message", "Booking deleted successfully", "id", id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics/trends")
    public ResponseEntity<Map<String, Object>> getBookingTrends() {
        return ResponseEntity.ok(bookingService.getBookingTrends());
    }

    @GetMapping("/instance-info")
    public ResponseEntity<Map<String, Object>> getInstanceInfo(
            @org.springframework.beans.factory.annotation.Value("${server.port:8083}") String port) {
        return ResponseEntity.ok(Map.of(
                "service", "booking-service",
                "port", port,
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
