package com.stayease.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates full discovery results combining StayEase verified inventory
 * and external accommodation discovery with clear source separation.
 */
public class DiscoveryResponse implements Serializable {

    private String query;
    private Double searchLatitude;
    private Double searchLongitude;
    private String resolvedLocationName;
    private Integer radiusKm = 10;
    private boolean gpsUsed = false;
    private List<StayEaseListingItem> stayEaseListings = new ArrayList<>();
    private List<ExternalDiscoveryResult> externalListings = new ArrayList<>();
    private String providerName;
    private String attribution;
    private String noticeMessage;

    public DiscoveryResponse() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Double getSearchLatitude() {
        return searchLatitude;
    }

    public void setSearchLatitude(Double searchLatitude) {
        this.searchLatitude = searchLatitude;
    }

    public Double getSearchLongitude() {
        return searchLongitude;
    }

    public void setSearchLongitude(Double searchLongitude) {
        this.searchLongitude = searchLongitude;
    }

    public String getResolvedLocationName() {
        return resolvedLocationName;
    }

    public void setResolvedLocationName(String resolvedLocationName) {
        this.resolvedLocationName = resolvedLocationName;
    }

    public Integer getRadiusKm() {
        return radiusKm;
    }

    public void setRadiusKm(Integer radiusKm) {
        this.radiusKm = radiusKm;
    }

    public boolean isGpsUsed() {
        return gpsUsed;
    }

    public void setGpsUsed(boolean gpsUsed) {
        this.gpsUsed = gpsUsed;
    }

    public List<StayEaseListingItem> getStayEaseListings() {
        return stayEaseListings;
    }

    public void setStayEaseListings(List<StayEaseListingItem> stayEaseListings) {
        this.stayEaseListings = stayEaseListings;
    }

    public List<ExternalDiscoveryResult> getExternalListings() {
        return externalListings;
    }

    public void setExternalListings(List<ExternalDiscoveryResult> externalListings) {
        this.externalListings = externalListings;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getAttribution() {
        return attribution;
    }

    public void setAttribution(String attribution) {
        this.attribution = attribution;
    }

    public String getNoticeMessage() {
        return noticeMessage;
    }

    public void setNoticeMessage(String noticeMessage) {
        this.noticeMessage = noticeMessage;
    }

    public int getTotalResults() {
        return stayEaseListings.size() + externalListings.size();
    }

    public boolean isLocationSet() {
        return (searchLatitude != null && searchLongitude != null) || (query != null && !query.trim().isEmpty());
    }
}
