package com.stayease.service;

import com.stayease.dto.AccommodationInquiryRequest;
import com.stayease.model.AccommodationInquiry;
import com.stayease.repository.AccommodationInquiryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AccommodationInquiryService {

    private static final Logger log = LoggerFactory.getLogger(AccommodationInquiryService.class);

    private final AccommodationInquiryRepository inquiryRepository;
    private final InAppNotificationService notificationService;

    public AccommodationInquiryService(AccommodationInquiryRepository inquiryRepository,
                                       InAppNotificationService notificationService) {
        this.inquiryRepository = inquiryRepository;
        this.notificationService = notificationService;
    }

    /**
     * Creates and records an accommodation inquiry and triggers real-time in-app alerts to admin.
     */
    @Transactional
    public AccommodationInquiry createInquiry(AccommodationInquiryRequest request, Authentication authentication) {
        String guestEmail = null;
        String guestName = request.getGuestName();

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            guestEmail = authentication.getName();
            if (guestName == null || guestName.isBlank()) {
                guestName = guestEmail;
            }
        }

        LocalDate checkIn = request.getCheckIn() != null ? request.getCheckIn() : LocalDate.now();
        LocalDate checkOut = request.getCheckOut() != null ? request.getCheckOut() : checkIn.plusDays(1);
        String hotelType = request.getHotelType() != null ? request.getHotelType() : "Accommodation";
        String hotelName = request.getHotelName() != null ? request.getHotelName() : "Local Stay";
        String guests = request.getGuests() != null ? request.getGuests() : "1";
        String phone = request.getPhone() != null ? request.getPhone() : "Not provided";

        AccommodationInquiry inquiry = new AccommodationInquiry(
                hotelName,
                hotelType,
                request.getHotelAddress(),
                request.getHotelPrice(),
                checkIn,
                checkOut,
                guests,
                phone,
                guestEmail,
                guestName,
                request.getNotes()
        );

        AccommodationInquiry saved = inquiryRepository.save(inquiry);
        log.info("New AccommodationInquiry registered: id={}, hotel={}, phone={}", saved.getId(), hotelName, phone);

        // Notify Admins in real time with high-visibility alert
        try {
            String adminTitle = "New Inquiry: " + hotelName;
            String guestDisplayName = (guestName != null && !guestName.isBlank()) ? guestName + " " : "";
            String adminMsg = String.format("Guest %s(Phone: %s%s) requested %s '%s' for %s to %s (%s guests). Notes: %s",
                    guestDisplayName,
                    phone,
                    guestEmail != null ? ", Email: " + guestEmail : "",
                    hotelType,
                    hotelName,
                    checkIn,
                    checkOut,
                    guests,
                    (request.getNotes() != null && !request.getNotes().isBlank()) ? request.getNotes() : "None"
            );

            notificationService.notifyAdmin(
                    adminTitle,
                    adminMsg,
                    "INQUIRY_NEW",
                    "/admin/inquiries"
            );
        } catch (Exception e) {
            log.warn("Failed to dispatch admin notification for inquiry {}: {}", saved.getId(), e.getMessage());
        }

        // Notify authenticated user if available
        if (guestEmail != null) {
            try {
                notificationService.notifyUser(
                        guestEmail,
                        "Inquiry Received: " + hotelName,
                        "Your reservation inquiry for " + hotelName + " (" + checkIn + " to " + checkOut + ") has been forwarded to StayEase concierge. We will contact you at " + phone + ".",
                        "INQUIRY_SENT",
                        "/discovery"
                );
            } catch (Exception e) {
                log.warn("Failed to dispatch user notification for inquiry {}: {}", saved.getId(), e.getMessage());
            }
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<AccommodationInquiry> getAllInquiries() {
        return inquiryRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        inquiryRepository.findById(id).ifPresent(inq -> {
            inq.setStatus(status);
            inquiryRepository.save(inq);
            log.info("Updated AccommodationInquiry id={} status to {}", id, status);
        });
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return inquiryRepository.countByStatus("PENDING");
    }
}
