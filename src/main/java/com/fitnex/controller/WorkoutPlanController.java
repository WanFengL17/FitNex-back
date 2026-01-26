package com.fitnex.controller;

import com.fitnex.entity.WorkoutPlan;
import com.fitnex.dto.WorkoutPlanSummaryDto;
import com.fitnex.service.WorkoutPlanService;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workout-plans")
@RequiredArgsConstructor
public class WorkoutPlanController {

    private final WorkoutPlanService workoutPlanService;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<List<WorkoutPlan>> getUserWorkoutPlans(Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(workoutPlanService.getUserWorkoutPlans(userId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<WorkoutPlanSummaryDto>> getWorkoutPlanHistory(Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(workoutPlanService.getWorkoutPlanHistory(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkoutPlan> getWorkoutPlan(@PathVariable Long id) {
        return ResponseEntity.ok(workoutPlanService.getWorkoutPlan(id));
    }

    @PostMapping
    public ResponseEntity<WorkoutPlan> createWorkoutPlan(
            @RequestBody WorkoutPlan plan,
            Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(workoutPlanService.createWorkoutPlan(userId, plan));
    }

    @PostMapping("/ai-generate")
    public ResponseEntity<WorkoutPlan> generateAIWorkoutPlan(Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(workoutPlanService.generateAIWorkoutPlan(userId));
    }

    @PostMapping("/{id}/adjust")
    public ResponseEntity<WorkoutPlan> adjustWorkoutPlan(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody(required = false) Map<String, String> feedback) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        String feedbackText = feedback != null ? feedback.get("feedback") : null;
        return ResponseEntity.ok(workoutPlanService.adjustWorkoutPlan(userId, id, feedbackText));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkoutPlan> updateWorkoutPlan(
            @PathVariable Long id,
            @RequestBody WorkoutPlan plan) {
        return ResponseEntity.ok(workoutPlanService.updateWorkoutPlan(id, plan));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkoutPlan(@PathVariable Long id) {
        workoutPlanService.deleteWorkoutPlan(id);
        return ResponseEntity.ok().build();
    }
}

