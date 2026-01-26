package com.fitnex.controller;

import com.fitnex.entity.NutritionRecord;
import com.fitnex.service.NutritionService;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping
    public ResponseEntity<List<NutritionRecord>> getUserNutritionRecords(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = getUserIdFromAuthentication(authentication);
        if (date != null) {
            return ResponseEntity.ok(nutritionService.getUserNutritionRecordsByDate(userId, date));
        }
        return ResponseEntity.ok(nutritionService.getUserNutritionRecords(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NutritionRecord> getNutritionRecord(@PathVariable Long id) {
        return ResponseEntity.ok(nutritionService.getNutritionRecord(id));
    }

    @PostMapping
    public ResponseEntity<NutritionRecord> createNutritionRecord(
            @RequestBody NutritionRecord record,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(nutritionService.createNutritionRecord(userId, record));
    }

    @PostMapping("/recognize")
    public ResponseEntity<NutritionRecord> recognizeFoodFromImage(
            @RequestParam("image") MultipartFile imageFile,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(nutritionService.recognizeFoodFromImage(userId, imageFile));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NutritionRecord> updateNutritionRecord(
            @PathVariable Long id,
            @RequestBody NutritionRecord record) {
        return ResponseEntity.ok(nutritionService.updateNutritionRecord(id, record));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNutritionRecord(@PathVariable Long id) {
        nutritionService.deleteNutritionRecord(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/daily-calories")
    public ResponseEntity<Integer> getDailyCalories(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = getUserIdFromAuthentication(authentication);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(nutritionService.getDailyCalories(userId, targetDate));
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<Map<String, Object>> getDailyNutritionSummary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = getUserIdFromAuthentication(authentication);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(nutritionService.getDailyNutritionSummary(userId, targetDate));
    }

    @GetMapping("/advice")
    public ResponseEntity<String> getPersonalizedNutritionAdvice(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = getUserIdFromAuthentication(authentication);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(nutritionService.getPersonalizedNutritionAdvice(userId, targetDate));
    }

    private final SecurityUtil securityUtil;

    private Long getUserIdFromAuthentication(Authentication authentication) {
        return securityUtil.getUserIdFromAuthentication(authentication);
    }
}

