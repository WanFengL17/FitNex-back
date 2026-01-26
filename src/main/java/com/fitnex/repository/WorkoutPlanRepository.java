package com.fitnex.repository;

import com.fitnex.entity.User;
import com.fitnex.entity.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {
    List<WorkoutPlan> findByUser(User user);
    List<WorkoutPlan> findByUserId(Long userId);
    List<WorkoutPlan> findByUserIdAndIsActive(Long userId, Boolean isActive);
}

