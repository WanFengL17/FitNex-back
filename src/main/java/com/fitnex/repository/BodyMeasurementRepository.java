package com.fitnex.repository;

import com.fitnex.entity.BodyMeasurement;
import com.fitnex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {
    List<BodyMeasurement> findByUser(User user);
    List<BodyMeasurement> findByUserId(Long userId);
    List<BodyMeasurement> findByUserIdOrderByMeasureDateDesc(Long userId);
    List<BodyMeasurement> findByUserIdAndMeasureDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}

