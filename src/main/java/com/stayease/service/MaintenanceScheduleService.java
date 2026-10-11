package com.stayease.service;

import com.stayease.exception.BookingConflictException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Room;
import com.stayease.model.RoomMaintenanceSchedule;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomMaintenanceScheduleRepository;
import com.stayease.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service managing room maintenance schedules with booking-safe inventory blocking.
 * Ensures no maintenance block can be created that conflicts with confirmed customer bookings.
 */
@Service
@Transactional(readOnly = true)
public class MaintenanceScheduleService {

    private final RoomMaintenanceScheduleRepository maintenanceRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogService auditLogService;

    public MaintenanceScheduleService(RoomMaintenanceScheduleRepository maintenanceRepository,
                                      RoomRepository roomRepository,
                                      BookingRepository bookingRepository,
                                      AuditLogService auditLogService) {
        this.maintenanceRepository = maintenanceRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Lists all scheduled maintenance blocks for a specific room.
     */
    public List<RoomMaintenanceSchedule> getSchedulesForRoom(Long roomId) {
        return maintenanceRepository.findByRoomIdOrderByStartDateDesc(roomId);
    }

    /**
     * Lists paginated maintenance schedules across the property.
     */
    public Page<RoomMaintenanceSchedule> getAllSchedules(Pageable pageable) {
        return maintenanceRepository.findAllWithRoom(pageable);
    }

    /**
     * Schedules a maintenance block for a room across [startDate, endDate].
     *
     * Validations:
     * 1. Room must exist.
     * 2. startDate must be strictly before endDate.
     * 3. Reason must be provided.
     * 4. Overlap check with existing active reservations:
     *    Checks for any PENDING, APPROVED, or CHECKED_IN bookings overlapping with [startDate, endDate].
     *    If found, throws {@link BookingConflictException} to prevent invalidating confirmed guest bookings.
     * 5. Checks for overlapping maintenance schedules for the same room.
     * 6. Audits the action.
     */
    @Transactional
    public RoomMaintenanceSchedule scheduleMaintenance(Long roomId, LocalDate startDate, LocalDate endDate,
                                                       String reason, String adminEmail) {
        if (roomId == null) {
            throw new IllegalArgumentException("Room ID is required.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason for maintenance is required.");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found with ID: " + roomId));

        // Booking-safe inventory check:
        // Do NOT allow maintenance that conflicts with existing confirmed/blocking bookings.
        List<Booking> conflicts = bookingRepository.findOverlapping(
                roomId,
                startDate,
                endDate,
                BookingStatus.BLOCKING
        );

        if (!conflicts.isEmpty()) {
            throw new BookingConflictException(
                    "Cannot schedule maintenance for Room " + room.getRoomNumber() +
                    " from " + startDate + " to " + endDate + " because " + conflicts.size() +
                    " active booking(s) already exist during this period.",
                    conflicts
            );
        }

        // Check overlapping maintenance schedules for the same room
        List<RoomMaintenanceSchedule> existingMaintenance = maintenanceRepository.findOverlapping(roomId, startDate, endDate);
        if (!existingMaintenance.isEmpty()) {
            throw new IllegalArgumentException(
                    "Room " + room.getRoomNumber() + " already has a scheduled maintenance block overlapping with " +
                    startDate + " to " + endDate + "."
            );
        }

        RoomMaintenanceSchedule schedule = new RoomMaintenanceSchedule(
                room,
                startDate,
                endDate,
                reason.trim(),
                adminEmail != null ? adminEmail : "ADMIN"
        );
        RoomMaintenanceSchedule saved = maintenanceRepository.save(schedule);

        auditLogService.log(
                adminEmail,
                "MAINTENANCE_SCHEDULED",
                "ROOM",
                String.valueOf(room.getId()),
                "Scheduled maintenance for Room " + room.getRoomNumber() + " (" + startDate + " to " + endDate + "): " + reason.trim()
        );

        return saved;
    }

    /**
     * Cancels or removes a scheduled maintenance block and audits the action.
     */
    @Transactional
    public void deleteMaintenanceSchedule(Long scheduleId, String adminEmail) {
        RoomMaintenanceSchedule schedule = maintenanceRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance schedule not found with ID: " + scheduleId));

        Room room = schedule.getRoom();
        String roomNumber = room != null ? room.getRoomNumber() : "Unknown";
        LocalDate start = schedule.getStartDate();
        LocalDate end = schedule.getEndDate();

        maintenanceRepository.delete(schedule);

        auditLogService.log(
                adminEmail,
                "MAINTENANCE_CANCELLED",
                "ROOM",
                room != null ? String.valueOf(room.getId()) : null,
                "Removed maintenance schedule #" + scheduleId + " for Room " + roomNumber + " (" + start + " to " + end + ")"
        );
    }
}
