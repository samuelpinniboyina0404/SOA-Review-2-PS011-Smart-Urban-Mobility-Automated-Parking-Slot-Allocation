package com.parkgrid.parking.controller;

import com.parkgrid.parking.entity.ParkingSlot;
import com.parkgrid.parking.entity.ParkingZone;
import com.parkgrid.parking.service.ParkingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parking")
public class ParkingController {

    @Autowired
    private ParkingService parkingService;

    // Public / Driver Endpoints
    @GetMapping("/zones")
    public ResponseEntity<List<ParkingZone>> getAllZones() {
        return ResponseEntity.ok(parkingService.getAllZones());
    }

    @GetMapping("/zones/{id}")
    public ResponseEntity<?> getZoneById(@PathVariable Long id) {
        return parkingService.getZoneById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zones/{zoneId}/slots")
    public ResponseEntity<List<ParkingSlot>> getSlotsByZone(@PathVariable Long zoneId) {
        return ResponseEntity.ok(parkingService.getSlotsByZone(zoneId));
    }

    @GetMapping("/slots/available")
    public ResponseEntity<List<ParkingSlot>> getAvailableSlots(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) String vehicleType) {
        return ResponseEntity.ok(parkingService.getAvailableSlots(zoneId, vehicleType));
    }

    @GetMapping("/slots/{slotId}")
    public ResponseEntity<ParkingSlot> getSlotById(@PathVariable Long slotId) {
        return ResponseEntity.ok(parkingService.getSlotById(slotId));
    }

    @GetMapping("/slots")
    public ResponseEntity<List<ParkingSlot>> getAllSlots() {
        return ResponseEntity.ok(parkingService.getAllSlots());
    }

    // Admin Endpoints
    @PostMapping("/zones")
    public ResponseEntity<ParkingZone> createZone(@RequestBody ParkingZone zone) {
        return ResponseEntity.ok(parkingService.createZone(zone));
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<ParkingZone> updateZone(@PathVariable Long id, @RequestBody ParkingZone details) {
        return ResponseEntity.ok(parkingService.updateZone(id, details));
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<?> deleteZone(@PathVariable Long id) {
        try {
            parkingService.deleteZone(id);
            return ResponseEntity.ok(Map.of("message", "Zone and associated slots deleted successfully", "id", id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/zones/{zoneId}/slots")
    public ResponseEntity<ParkingSlot> addSlotToZone(@PathVariable Long zoneId, @RequestBody ParkingSlot slot) {
        return ResponseEntity.ok(parkingService.addSlotToZone(zoneId, slot));
    }

    @PutMapping("/slots/{slotId}")
    public ResponseEntity<ParkingSlot> updateSlot(@PathVariable Long slotId, @RequestBody ParkingSlot slot) {
        return ResponseEntity.ok(parkingService.updateSlot(slotId, slot));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<?> deleteSlot(@PathVariable Long slotId) {
        try {
            parkingService.deleteSlot(slotId);
            return ResponseEntity.ok(Map.of("message", "Slot deleted successfully", "id", slotId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/slots/{slotId}/status")
    public ResponseEntity<ParkingSlot> updateSlotStatus(
            @PathVariable Long slotId,
            @RequestParam String status) {
        return ResponseEntity.ok(parkingService.updateSlotStatus(slotId, status));
    }

    @GetMapping("/analytics/occupancy")
    public ResponseEntity<Map<String, Object>> getOccupancyMetrics() {
        return ResponseEntity.ok(parkingService.getOccupancyMetrics());
    }

    @GetMapping("/instance-info")
    public ResponseEntity<Map<String, Object>> getInstanceInfo(
            @org.springframework.beans.factory.annotation.Value("${server.port:8082}") String port) {
        return ResponseEntity.ok(Map.of(
                "service", "parking-service",
                "port", port,
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }

    // Inter-Service / Internal Booking Endpoints
    @PutMapping("/slots/{slotId}/reserve")
    public ResponseEntity<?> reserveSlot(@PathVariable Long slotId) {
        try {
            ParkingSlot reserved = parkingService.reserveSlot(slotId);
            return ResponseEntity.ok(reserved);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/slots/{slotId}/release")
    public ResponseEntity<?> releaseSlot(@PathVariable Long slotId) {
        try {
            ParkingSlot released = parkingService.releaseSlot(slotId);
            return ResponseEntity.ok(released);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/slots/{slotId}/occupy")
    public ResponseEntity<?> occupySlot(@PathVariable Long slotId) {
        try {
            ParkingSlot occupied = parkingService.occupySlot(slotId);
            return ResponseEntity.ok(occupied);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
