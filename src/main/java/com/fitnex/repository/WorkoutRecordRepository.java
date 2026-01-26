package com.fitnex.repository;

import com.fitnex.entity.User;
import com.fitnex.entity.WorkoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, Long> {
    List<WorkoutRecord> findByUser(User user);
    List<WorkoutRecord> findByUserId(Long userId);
    List<WorkoutRecord> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<WorkoutRecord> findByWorkoutPlanId(Long planId);
    List<WorkoutRecord> findByWorkoutPlanIdAndStartTimeBetween(Long planId, LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT SUM(wr.caloriesBurned) FROM WorkoutRecord wr WHERE wr.user.id = :userId AND wr.startTime >= :startDate")
    Integer sumCaloriesBurnedByUserIdAndDate(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT DISTINCT wr FROM WorkoutRecord wr LEFT JOIN FETCH wr.exerciseRecords WHERE wr.user.id = :userId AND wr.startTime BETWEEN :start AND :end")
    List<WorkoutRecord> findByUserIdAndStartTimeBetweenWithExercises(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

