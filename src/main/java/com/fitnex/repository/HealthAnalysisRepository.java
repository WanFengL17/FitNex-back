package com.fitnex.repository;

import com.fitnex.entity.HealthAnalysis;
import com.fitnex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthAnalysisRepository extends JpaRepository<HealthAnalysis, Long> {
    List<HealthAnalysis> findByUser(User user);
    List<HealthAnalysis> findByUserId(Long userId);
    Optional<HealthAnalysis> findByUserIdAndAnalysisDate(Long userId, LocalDate analysisDate);
    List<HealthAnalysis> findByUserIdOrderByAnalysisDateDesc(Long userId);
}

