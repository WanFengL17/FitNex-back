package com.fitnex.repository;

import com.fitnex.entity.NutritionRecord;
import com.fitnex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NutritionRecordRepository extends JpaRepository<NutritionRecord, Long> {
    List<NutritionRecord> findByUser(User user);
    List<NutritionRecord> findByUserId(Long userId);
    List<NutritionRecord> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);
    List<NutritionRecord> findByUserIdAndRecordDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT SUM(nr.calories) FROM NutritionRecord nr WHERE nr.user.id = :userId AND nr.recordDate = :date")
    Integer sumCaloriesByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}

