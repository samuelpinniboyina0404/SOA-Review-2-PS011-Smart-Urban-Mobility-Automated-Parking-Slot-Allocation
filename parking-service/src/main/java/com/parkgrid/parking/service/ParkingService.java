package com.parkgrid.parking.service;

import com.parkgrid.parking.entity.ParkingSlot;
import com.parkgrid.parking.entity.ParkingZone;
import com.parkgrid.parking.repository.ParkingSlotRepository;
import com.parkgrid.parking.repository.ParkingZoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ParkingService {

    @Autowired
    private ParkingZoneRepository zoneRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    public List<ParkingZone> getAllZones() {
        return zoneRepository.findAll();
    }

    public Optional<ParkingZone> getZoneById(Long id) {
        return zoneRepository.findById(id);
    }

    public ParkingZone createZone(ParkingZone zone) {
        if (zone.getAvailableCapacity() == null) {
            zone.setAvailableCapacity(zone.getTotalCapacity());
        }
        return zoneRepository.save(zone);
    }

    public ParkingZone updateZone(Long id, ParkingZone details) {
        ParkingZone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone not found with ID: " + id));
        zone.setName(details.getName());
        zone.setLocation(details.getLocation());
        zone.setTotalCapacity(details.getTotalCapacity());
        zone.setHourlyRate(details.getHourlyRate());
        zone.setVehicleType(details.getVehicleType());
        if (details.getActive() != null) zone.setActive(details.getActive());
        return zoneRepository.save(zone);
    }

    @Transactional
    public void deleteZone(Long id) {
        ParkingZone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone not found with ID: " + id));
        List<ParkingSlot> slots = slotRepository.findByZoneId(id);
        slotRepository.deleteAll(slots);
        zoneRepository.delete(zone);
    }

    public List<ParkingSlot> getAllSlots() {
        return slotRepository.findAll();
    }

    public List<ParkingSlot> getSlotsByZone(Long zoneId) {
        return slotRepository.findByZoneId(zoneId);
    }

    public List<ParkingSlot> getAvailableSlots(Long zoneId, String vehicleType) {
        if (zoneId != null) {
            return slotRepository.findByZoneIdAndStatus(zoneId, "AVAILABLE");
        } else if (vehicleType != null) {
            List<ParkingZone> zones = zoneRepository.findByVehicleType(vehicleType);
            List<ParkingSlot> result = new ArrayList<>();
            for (ParkingZone z : zones) {
                result.addAll(slotRepository.findByZoneIdAndStatus(z.getId(), "AVAILABLE"));
            }
            return result;
        } else {
            return slotRepository.findByStatus("AVAILABLE");
        }
    }

    public ParkingSlot getSlotById(Long slotId) {
        return slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with ID: " + slotId));
    }

    public ParkingSlot addSlotToZone(Long zoneId, ParkingSlot slot) {
        ParkingZone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new RuntimeException("Zone not found with ID: " + zoneId));
        slot.setZoneId(zoneId);
        if (slot.getStatus() == null) slot.setStatus("AVAILABLE");
        ParkingSlot saved = slotRepository.save(slot);

        recalculateZoneCapacity(zoneId);
        return saved;
    }

    @Transactional
    public ParkingSlot updateSlotStatus(Long slotId, String status) {
        ParkingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with ID: " + slotId));
        slot.setStatus(status.toUpperCase());
        ParkingSlot updated = slotRepository.save(slot);
        recalculateZoneCapacity(slot.getZoneId());
        return updated;
    }

    @Transactional
    public ParkingSlot updateSlot(Long slotId, ParkingSlot details) {
        ParkingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with ID: " + slotId));
        if (details.getSlotNumber() != null && !details.getSlotNumber().isBlank()) {
            slot.setSlotNumber(details.getSlotNumber());
        }
        if (details.getFloorNumber() != null) {
            slot.setFloorNumber(details.getFloorNumber());
        }
        if (details.getSlotType() != null && !details.getSlotType().isBlank()) {
            slot.setSlotType(details.getSlotType());
        }
        if (details.getStatus() != null && !details.getStatus().isBlank()) {
            slot.setStatus(details.getStatus().toUpperCase());
        }
        ParkingSlot saved = slotRepository.save(slot);
        recalculateZoneCapacity(slot.getZoneId());
        return saved;
    }

    @Transactional
    public void deleteSlot(Long slotId) {
        ParkingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with ID: " + slotId));
        Long zoneId = slot.getZoneId();
        slotRepository.delete(slot);
        recalculateZoneCapacity(zoneId);
    }

    @Transactional
    public ParkingSlot reserveSlot(Long slotId) {
        ParkingSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with ID: " + slotId));

        if (!"AVAILABLE".equalsIgnoreCase(slot.getStatus())) {
            throw new IllegalStateException("Slot " + slot.getSlotNumber() + " is currently " + slot.getStatus() + " and cannot be reserved.");
        }

        slot.setStatus("RESERVED");
        ParkingSlot saved = slotRepository.save(slot);
        recalculateZoneCapacity(slot.getZoneId());
        return saved;
    }

    @Transactional
    public ParkingSlot releaseSlot(Long slotId) {
        ParkingSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with ID: " + slotId));

        slot.setStatus("AVAILABLE");
        ParkingSlot saved = slotRepository.save(slot);
        recalculateZoneCapacity(slot.getZoneId());
        return saved;
    }

    @Transactional
    public ParkingSlot occupySlot(Long slotId) {
        ParkingSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with ID: " + slotId));

        slot.setStatus("OCCUPIED");
        ParkingSlot saved = slotRepository.save(slot);
        recalculateZoneCapacity(slot.getZoneId());
        return saved;
    }

    public Map<String, Object> getOccupancyMetrics() {
        List<ParkingZone> zones = zoneRepository.findAll();
        List<Map<String, Object>> zoneMetrics = new ArrayList<>();
        int totalCap = 0;
        int totalAvail = 0;

        for (ParkingZone zone : zones) {
            long available = slotRepository.countByZoneIdAndStatus(zone.getId(), "AVAILABLE");
            long reserved = slotRepository.countByZoneIdAndStatus(zone.getId(), "RESERVED");
            long occupied = slotRepository.countByZoneIdAndStatus(zone.getId(), "OCCUPIED");
            long maintenance = slotRepository.countByZoneIdAndStatus(zone.getId(), "MAINTENANCE");

            int zoneTotal = zone.getTotalCapacity();
            totalCap += zoneTotal;
            totalAvail += available;

            double occupancyRate = zoneTotal > 0 ? ((double) (occupied + reserved) / zoneTotal) * 100.0 : 0.0;

            Map<String, Object> metric = new HashMap<>();
            metric.put("zoneId", zone.getId());
            metric.put("zoneName", zone.getName());
            metric.put("vehicleType", zone.getVehicleType());
            metric.put("totalCapacity", zoneTotal);
            metric.put("availableCount", available);
            metric.put("reservedCount", reserved);
            metric.put("occupiedCount", occupied);
            metric.put("maintenanceCount", maintenance);
            metric.put("occupancyPercentage", Math.round(occupancyRate * 10.0) / 10.0);
            zoneMetrics.add(metric);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalFacilityCapacity", totalCap);
        summary.put("totalAvailableSlots", totalAvail);
        summary.put("totalOccupiedOrReserved", totalCap - totalAvail);
        summary.put("overallOccupancyPercentage", totalCap > 0 ? Math.round(((double)(totalCap - totalAvail) / totalCap) * 1000.0) / 10.0 : 0.0);
        summary.put("zones", zoneMetrics);

        return summary;
    }

    private void recalculateZoneCapacity(Long zoneId) {
        ParkingZone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone != null) {
            long availCount = slotRepository.countByZoneIdAndStatus(zoneId, "AVAILABLE");
            zone.setAvailableCapacity((int) availCount);
            zoneRepository.save(zone);
        }
    }
}
