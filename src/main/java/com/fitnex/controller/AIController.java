package com.fitnex.controller;

import com.fitnex.entity.HealthProfile;
import com.fitnex.entity.NutritionRecord;
import com.fitnex.repository.HealthProfileRepository;
import com.fitnex.repository.NutritionRecordRepository;
import com.fitnex.service.NutritionService;
import com.fitnex.service.ai.AIService;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;
    private final SecurityUtil securityUtil;
    private final HealthProfileRepository healthProfileRepository;
    private final NutritionRecordRepository nutritionRecordRepository;
    private final NutritionService nutritionService;

    /**
     * 获取个性化饮食建议
     */
    @PostMapping("/nutrition-advice")
    public ResponseEntity<Map<String, Object>> getNutritionAdvice(
            Authentication authentication,
            @RequestParam(required = false) String date) {
        Long userId = getUserIdFromAuthentication(authentication);
        
        // 获取用户健康档案
        HealthProfile profile = healthProfileRepository.findByUserId(userId).orElse(null);
        
        // 获取指定日期的营养数据，如果没有指定则使用今天
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        Map<String, Object> summary = nutritionService.getDailyNutritionSummary(userId, targetDate);
        
        Integer dailyCalories = (Integer) summary.get("calories");
        Integer targetCalories = (Integer) summary.get("targetCalories");
        Double totalProtein = (Double) summary.get("protein");
        Double totalCarbs = (Double) summary.get("carbs");
        Double totalFat = (Double) summary.get("fat");
        
        // 调用AI获取建议
        String advice = aiService.getNutritionAdvice(userId, profile, dailyCalories, 
                targetCalories, totalProtein, totalCarbs, totalFat);
        
        Map<String, Object> response = new HashMap<>();
        response.put("advice", advice);
        response.put("summary", summary);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 智能问答 - 解答健身疑惑和健康咨询
     */
    @PostMapping("/question")
    public ResponseEntity<Map<String, Object>> answerFitnessQuestion(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String question = request.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "问题不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        
        // 检查是否是模型相关的问题
        String lowerQuestion = question.toLowerCase();
        if (lowerQuestion.contains("什么模型") || lowerQuestion.contains("谁") || 
            lowerQuestion.contains("你是谁") || lowerQuestion.contains("什么ai") ||
            lowerQuestion.contains("什么助手") || lowerQuestion.contains("模型名称")) {
            Map<String, Object> response = new HashMap<>();
            response.put("answer", "您好，我是依托default模型的智能助手，在Cursor IDE中为您提供代码编写和问题解答服务，你可以直接告诉我你的需求。");
            return ResponseEntity.ok(response);
        }
        
        String answer = aiService.answerFitnessQuestion(question);
        
        Map<String, Object> response = new HashMap<>();
        response.put("answer", answer);
        response.put("question", question);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 生成AI健身计划（已通过WorkoutPlanController实现，这里保留作为备用）
     */
    @PostMapping("/workout-plan")
    public ResponseEntity<Map<String, Object>> generateWorkoutPlan(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "请使用 /workout-plans/ai-generate 端点生成训练计划");
        response.put("endpoint", "/workout-plans/ai-generate");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 识别食物图片（已通过NutritionController实现，这里保留作为备用）
     */
    @PostMapping("/recognize-food")
    public ResponseEntity<Map<String, Object>> recognizeFood(
            @RequestParam("image") MultipartFile imageFile,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "请使用 /nutrition/recognize 端点识别食物");
        response.put("endpoint", "/nutrition/recognize");
        
        return ResponseEntity.ok(response);
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        return securityUtil.getUserIdFromAuthentication(authentication);
    }
}

