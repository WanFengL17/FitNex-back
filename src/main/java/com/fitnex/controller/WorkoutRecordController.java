package com.fitnex.controller;

import com.fitnex.entity.WorkoutRecord;
import com.fitnex.entity.WorkoutExerciseRecord;
import com.fitnex.service.WorkoutRecordService;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workout-records")
@RequiredArgsConstructor
public class WorkoutRecordController {

    private final WorkoutRecordService workoutRecordService;

    @GetMapping
    public ResponseEntity<List<WorkoutRecord>> getUserWorkoutRecords(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        Long userId = getUserIdFromAuthentication(authentication);
        if (start != null && end != null) {
            return ResponseEntity.ok(workoutRecordService.getUserWorkoutRecordsByDateRange(userId, start, end));
        }
        return ResponseEntity.ok(workoutRecordService.getUserWorkoutRecords(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkoutRecord> getWorkoutRecord(@PathVariable Long id) {
        return ResponseEntity.ok(workoutRecordService.getWorkoutRecord(id));
    }

    @PostMapping
    public ResponseEntity<WorkoutRecord> createWorkoutRecord(
            @RequestBody WorkoutRecord record,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(workoutRecordService.createWorkoutRecord(userId, record));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkoutRecord> updateWorkoutRecord(
            @PathVariable Long id,
            @RequestBody WorkoutRecord record) {
        return ResponseEntity.ok(workoutRecordService.updateWorkoutRecord(id, record));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkoutRecord(@PathVariable Long id) {
        workoutRecordService.deleteWorkoutRecord(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/start")
    public ResponseEntity<WorkoutRecord> startWorkout(
            Authentication authentication,
            @RequestParam(required = false) Long planId) {
        Long userId = getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(workoutRecordService.startWorkout(userId, planId));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<WorkoutRecord> pauseWorkout(@PathVariable Long id) {
        return ResponseEntity.ok(workoutRecordService.pauseWorkout(id));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<WorkoutRecord> resumeWorkout(@PathVariable Long id) {
        return ResponseEntity.ok(workoutRecordService.resumeWorkout(id));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<WorkoutRecord> endWorkout(@PathVariable Long id) {
        return ResponseEntity.ok(workoutRecordService.endWorkout(id));
    }

    @PutMapping("/{id}/progress")
    public ResponseEntity<WorkoutRecord> updateWorkoutProgress(
            @PathVariable Long id,
            @RequestBody WorkoutRecord progress) {
        return ResponseEntity.ok(workoutRecordService.updateWorkoutProgress(id, progress));
    }

    @PutMapping("/{id}/exercises/{exerciseId}")
    public ResponseEntity<WorkoutRecord> updateExerciseProgress(
            @PathVariable Long id,
            @PathVariable Long exerciseId,
            @RequestBody WorkoutExerciseRecord progress) {
        return ResponseEntity.ok(workoutRecordService.updateExerciseProgress(id, exerciseId, progress));
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<WorkoutRecord> shareWorkoutRecord(@PathVariable Long id) {
        return ResponseEntity.ok(workoutRecordService.shareWorkoutRecord(id));
    }

    @GetMapping("/share/{shareCode}")
    public ResponseEntity<WorkoutRecord> getWorkoutRecordByShareCode(@PathVariable String shareCode) {
        return ResponseEntity.ok(workoutRecordService.getWorkoutRecordByShareCode(shareCode));
    }

    private final SecurityUtil securityUtil;

    private Long getUserIdFromAuthentication(Authentication authentication) {
        return securityUtil.getUserIdFromAuthentication(authentication);
    }
}

