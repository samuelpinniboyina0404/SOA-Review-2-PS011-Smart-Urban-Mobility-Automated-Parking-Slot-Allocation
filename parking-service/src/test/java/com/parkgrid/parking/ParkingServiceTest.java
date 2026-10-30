package com.parkgrid.parking;

import com.parkgrid.parking.entity.ParkingSlot;
import com.parkgrid.parking.entity.ParkingZone;
import com.parkgrid.parking.service.ParkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "eureka.client.enabled=false")
public class ParkingServiceTest {

    @Autowired
    private ParkingService parkingService;

    @Test
    public void testZoneAndSlotInitialState() {
        List<ParkingZone> zones = parkingService.getAllZones();
        assertFalse(zones.isEmpty(), "Parking zones should be initialized");

        ParkingZone zoneA = zones.get(0);
        List<ParkingSlot> slots = parkingService.getSlotsByZone(zoneA.getId());
        assertFalse(slots.isEmpty(), "Parking slots should be created for zone A");
    }

    @Test
    public void testSlotReservationLifecycle() {
        List<ParkingSlot> availSlots = parkingService.getAvailableSlots(null, null);
        assertFalse(availSlots.isEmpty(), "Available slots should exist");

        ParkingSlot slot = availSlots.get(0);
        Long slotId = slot.getId();

        ParkingSlot reserved = parkingService.reserveSlot(slotId);
        assertEquals("RESERVED", reserved.getStatus());

        // Attempting to reserve an already reserved slot must throw IllegalStateException (Zero Double Booking)
        assertThrows(IllegalStateException.class, () -> parkingService.reserveSlot(slotId));

        ParkingSlot occupied = parkingService.occupySlot(slotId);
        assertEquals("OCCUPIED", occupied.getStatus());

        ParkingSlot released = parkingService.releaseSlot(slotId);
        assertEquals("AVAILABLE", released.getStatus());
    }

    @Test
    public void testOccupancyMetrics() {
        Map<String, Object> metrics = parkingService.getOccupancyMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("overallOccupancyPercentage"));
    }

    @Test
    public void testZoneAndSlotCrudOperations() {
        // 1. Create Zone
        ParkingZone newZone = new ParkingZone("Test Zone X", "Building X", 5, 6.0, "CAR");
        ParkingZone createdZone = parkingService.createZone(newZone);
        assertNotNull(createdZone.getId());
        assertEquals("Test Zone X", createdZone.getName());

        // 2. Update Zone
        createdZone.setName("Updated Zone X");
        ParkingZone updatedZone = parkingService.updateZone(createdZone.getId(), createdZone);
        assertEquals("Updated Zone X", updatedZone.getName());

        // 3. Create Slot
        ParkingSlot newSlot = new ParkingSlot("X-999", createdZone.getId(), 1, "STANDARD", "AVAILABLE");
        ParkingSlot createdSlot = parkingService.addSlotToZone(createdZone.getId(), newSlot);
        assertNotNull(createdSlot.getId());

        // 4. Update Slot
        createdSlot.setSlotType("HANDICAPPED");
        ParkingSlot updatedSlot = parkingService.updateSlot(createdSlot.getId(), createdSlot);
        assertEquals("HANDICAPPED", updatedSlot.getSlotType());

        // 5. Delete Slot
        parkingService.deleteSlot(createdSlot.getId());
        assertThrows(RuntimeException.class, () -> parkingService.getSlotById(createdSlot.getId()));

        // 6. Delete Zone
        parkingService.deleteZone(createdZone.getId());
        assertFalse(parkingService.getZoneById(createdZone.getId()).isPresent());
    }
}
