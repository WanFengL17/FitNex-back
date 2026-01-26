package com.fitnex.controller;

import com.fitnex.entity.HealthAnalysis;
import com.fitnex.service.HealthAnalysisService;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/health-analysis")
@RequiredArgsConstructor
public class HealthAnalysisController {

    private final HealthAnalysisService healthAnalysisService;
    private final SecurityUtil securityUtil;

    @GetMapping("/latest")
    public ResponseEntity<HealthAnalysis> getLatestAnalysis(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        HealthAnalysis analysis = healthAnalysisService.getLatestAnalysis(userId);
        return ResponseEntity.ok(analysis);
    }

    @PostMapping("/generate")
    public ResponseEntity<HealthAnalysis> generateHealthAnalysis(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(healthAnalysisService.generateHealthAnalysis(userId));
    }

    @GetMapping("/weight-history")
    public ResponseEntity<Map<String, Object>> getWeightHistory(
            Authentication authentication,
            @RequestParam(required = false) Integer days) {
        Long userId = getUserIdFromAuthentication(authentication);
        Map<String, Object> history = healthAnalysisService.getWeightHistory(userId, days != null ? days : 90);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/workout-heatmap")
    public ResponseEntity<Map<String, Object>> getWorkoutHeatmap(
            Authentication authentication,
            @RequestParam(required = false) Integer days) {
        Long userId = getUserIdFromAuthentication(authentication);
        Map<String, Object> heatmap = healthAnalysisService.getWorkoutHeatmap(userId, days != null ? days : 30);
        return ResponseEntity.ok(heatmap);
    }

    @GetMapping("/progress-tracking")
    public ResponseEntity<Map<String, Object>> getProgressTracking(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        Map<String, Object> progress = healthAnalysisService.getProgressTracking(userId);
        return ResponseEntity.ok(progress);
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        return securityUtil.getUserIdFromAuthentication(authentication);
    }
}

