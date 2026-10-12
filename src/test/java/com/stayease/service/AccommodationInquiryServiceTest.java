package com.stayease.service;

import com.stayease.dto.AccommodationInquiryRequest;
import com.stayease.model.AccommodationInquiry;
import com.stayease.repository.AccommodationInquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccommodationInquiryServiceTest {

    @Mock
    private AccommodationInquiryRepository inquiryRepository;

    @Mock
    private InAppNotificationService notificationService;

    @InjectMocks
    private AccommodationInquiryService inquiryService;

    private AccommodationInquiryRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = new AccommodationInquiryRequest();
        sampleRequest.setHotelName("The Pink Nest");
        sampleRequest.setHotelType("Guest House");
        sampleRequest.setHotelAddress("Ramganj, Jaipur");
        sampleRequest.setHotelPrice(650.0);
        sampleRequest.setGuestName("Aarav Sharma");
        sampleRequest.setPhone("+91 9876543210");
        sampleRequest.setCheckIn(LocalDate.of(2026, 11, 1));
        sampleRequest.setCheckOut(LocalDate.of(2026, 11, 5));
        sampleRequest.setGuests("2");
        sampleRequest.setNotes("Looking for quiet room with Wi-Fi");
    }

    @Test
    void testCreateInquiry_NotifiesAdminAndSaves() {
        when(inquiryRepository.save(any(AccommodationInquiry.class)))
                .thenAnswer(invocation -> {
                    AccommodationInquiry inq = invocation.getArgument(0);
                    inq.setId(1L);
                    return inq;
                });

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "aarav@example.com",
                "password",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );
        AccommodationInquiry result = inquiryService.createInquiry(sampleRequest, auth);

        assertNotNull(result);
        assertEquals("The Pink Nest", result.getHotelName());
        assertEquals("Aarav Sharma", result.getGuestName());
        assertEquals("+91 9876543210", result.getContactPhone());
        assertEquals("PENDING", result.getStatus());
        assertEquals("aarav@example.com", result.getGuestEmail());

        // Verify notification to admin was dispatched
        verify(notificationService, times(1)).notifyAdmin(
                contains("The Pink Nest"),
                contains("Aarav Sharma"),
                eq("INQUIRY_NEW"),
                eq("/admin/inquiries")
        );

        // Verify user notification dispatched
        verify(notificationService, times(1)).notifyUser(
                eq("aarav@example.com"),
                contains("Inquiry Received"),
                contains("The Pink Nest"),
                eq("INQUIRY_SENT"),
                eq("/discovery")
        );
    }

    @Test
    void testCreateInquiry_AnonymousGuest() {
        when(inquiryRepository.save(any(AccommodationInquiry.class)))
                .thenAnswer(invocation -> {
                    AccommodationInquiry inq = invocation.getArgument(0);
                    inq.setId(2L);
                    return inq;
                });

        AccommodationInquiry result = inquiryService.createInquiry(sampleRequest, null);

        assertNotNull(result);
        assertNull(result.getGuestEmail());
        assertEquals("The Pink Nest", result.getHotelName());

        // Verify admin is notified even for unauthenticated inquiries
        verify(notificationService, times(1)).notifyAdmin(
                contains("The Pink Nest"),
                anyString(),
                eq("INQUIRY_NEW"),
                eq("/admin/inquiries")
        );

        // No user notification when guestEmail is null
        verify(notificationService, never()).notifyUser(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testUpdateStatus_ValidId() {
        AccommodationInquiry inquiry = new AccommodationInquiry();
        inquiry.setId(10L);
        inquiry.setStatus("PENDING");

        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));
        when(inquiryRepository.save(any(AccommodationInquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inquiryService.updateStatus(10L, "CONTACTED");

        assertEquals("CONTACTED", inquiry.getStatus());
        verify(inquiryRepository).save(inquiry);
    }
}
