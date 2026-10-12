package com.stayease.repository;

import com.stayease.model.AccommodationInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccommodationInquiryRepository extends JpaRepository<AccommodationInquiry, Long> {

    List<AccommodationInquiry> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
