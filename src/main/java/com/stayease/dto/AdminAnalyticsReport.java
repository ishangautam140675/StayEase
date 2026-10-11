package com.stayease.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO carrying accurate operational and financial reporting metrics
 * computed from persisted database records across a date range.
 */
public class AdminAnalyticsReport {

    private LocalDate startDate;
    private LocalDate endDate;
    private long totalBookings;
    private long approvedBookings;
    private long checkedInBookings;
    private long checkedOutBookings;
    private long noShowBookings;
    private long cancelledBookings;
    private long rejectedBookings;
    private BigDecimal totalRevenue;
    private BigDecimal completedRevenue;
    private double cancellationRate;
    private double averageOccupancyRate;
    private BigDecimal adr = BigDecimal.ZERO;
    private BigDecimal revPar = BigDecimal.ZERO;
    private double averageLeadTimeDays = 0.0;
    private long totalRoomNightsOccupied = 0;
    private long totalRoomNightsPossible = 0;
    private List<RoomUtilizationStat> roomUtilizations;

    public AdminAnalyticsReport() {
    }

    public AdminAnalyticsReport(LocalDate startDate, LocalDate endDate, long totalBookings, long approvedBookings,
                                long checkedInBookings, long checkedOutBookings, long noShowBookings,
                                long cancelledBookings, long rejectedBookings, BigDecimal totalRevenue,
                                BigDecimal completedRevenue, double cancellationRate, double averageOccupancyRate,
                                List<RoomUtilizationStat> roomUtilizations) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalBookings = totalBookings;
        this.approvedBookings = approvedBookings;
        this.checkedInBookings = checkedInBookings;
        this.checkedOutBookings = checkedOutBookings;
        this.noShowBookings = noShowBookings;
        this.cancelledBookings = cancelledBookings;
        this.rejectedBookings = rejectedBookings;
        this.totalRevenue = totalRevenue;
        this.completedRevenue = completedRevenue;
        this.cancellationRate = cancellationRate;
        this.averageOccupancyRate = averageOccupancyRate;
        this.roomUtilizations = roomUtilizations;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public long getTotalBookings() {
        return totalBookings;
    }

    public void setTotalBookings(long totalBookings) {
        this.totalBookings = totalBookings;
    }

    public long getApprovedBookings() {
        return approvedBookings;
    }

    public void setApprovedBookings(long approvedBookings) {
        this.approvedBookings = approvedBookings;
    }

    public long getCheckedInBookings() {
        return checkedInBookings;
    }

    public void setCheckedInBookings(long checkedInBookings) {
        this.checkedInBookings = checkedInBookings;
    }

    public long getCheckedOutBookings() {
        return checkedOutBookings;
    }

    public void setCheckedOutBookings(long checkedOutBookings) {
        this.checkedOutBookings = checkedOutBookings;
    }

    public long getNoShowBookings() {
        return noShowBookings;
    }

    public void setNoShowBookings(long noShowBookings) {
        this.noShowBookings = noShowBookings;
    }

    public long getCancelledBookings() {
        return cancelledBookings;
    }

    public void setCancelledBookings(long cancelledBookings) {
        this.cancelledBookings = cancelledBookings;
    }

    public long getRejectedBookings() {
        return rejectedBookings;
    }

    public void setRejectedBookings(long rejectedBookings) {
        this.rejectedBookings = rejectedBookings;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getCompletedRevenue() {
        return completedRevenue != null ? completedRevenue : BigDecimal.ZERO;
    }

    public void setCompletedRevenue(BigDecimal completedRevenue) {
        this.completedRevenue = completedRevenue;
    }

    public double getCancellationRate() {
        return cancellationRate;
    }

    public void setCancellationRate(double cancellationRate) {
        this.cancellationRate = cancellationRate;
    }

    public double getAverageOccupancyRate() {
        return averageOccupancyRate;
    }

    public void setAverageOccupancyRate(double averageOccupancyRate) {
        this.averageOccupancyRate = averageOccupancyRate;
    }

    public List<RoomUtilizationStat> getRoomUtilizations() {
        return roomUtilizations;
    }

    public void setRoomUtilizations(List<RoomUtilizationStat> roomUtilizations) {
        this.roomUtilizations = roomUtilizations;
    }

    public BigDecimal getAdr() {
        return adr != null ? adr : BigDecimal.ZERO;
    }

    public void setAdr(BigDecimal adr) {
        this.adr = adr;
    }

    public BigDecimal getRevPar() {
        return revPar != null ? revPar : BigDecimal.ZERO;
    }

    public void setRevPar(BigDecimal revPar) {
        this.revPar = revPar;
    }

    public double getAverageLeadTimeDays() {
        return averageLeadTimeDays;
    }

    public void setAverageLeadTimeDays(double averageLeadTimeDays) {
        this.averageLeadTimeDays = averageLeadTimeDays;
    }

    public long getTotalRoomNightsOccupied() {
        return totalRoomNightsOccupied;
    }

    public void setTotalRoomNightsOccupied(long totalRoomNightsOccupied) {
        this.totalRoomNightsOccupied = totalRoomNightsOccupied;
    }

    public long getTotalRoomNightsPossible() {
        return totalRoomNightsPossible;
    }

    public void setTotalRoomNightsPossible(long totalRoomNightsPossible) {
        this.totalRoomNightsPossible = totalRoomNightsPossible;
    }

    public static class RoomUtilizationStat {
        private Long roomId;
        private String roomNumber;
        private String roomType;
        private long nightsOccupied;
        private double utilizationRate;
        private BigDecimal revenue;

        public RoomUtilizationStat() {
        }

        public RoomUtilizationStat(Long roomId, String roomNumber, String roomType, long nightsOccupied,
                                   double utilizationRate, BigDecimal revenue) {
            this.roomId = roomId;
            this.roomNumber = roomNumber;
            this.roomType = roomType;
            this.nightsOccupied = nightsOccupied;
            this.utilizationRate = utilizationRate;
            this.revenue = revenue;
        }

        public Long getRoomId() {
            return roomId;
        }

        public String getRoomNumber() {
            return roomNumber;
        }

        public String getRoomType() {
            return roomType;
        }

        public long getNightsOccupied() {
            return nightsOccupied;
        }

        public double getUtilizationRate() {
            return utilizationRate;
        }

        public BigDecimal getRevenue() {
            return revenue != null ? revenue : BigDecimal.ZERO;
        }
    }
}
