package com.fitnex.service;

import com.fitnex.entity.HealthProfile;
import com.fitnex.entity.NutritionRecord;
import com.fitnex.entity.User;
import com.fitnex.repository.NutritionRecordRepository;
import com.fitnex.repository.UserRepository;
import com.fitnex.service.ai.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NutritionService {

    private final NutritionRecordRepository nutritionRecordRepository;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final FileService fileService;
    private final HealthProfileService healthProfileService;

    public List<NutritionRecord> getUserNutritionRecords(Long userId) {
        return nutritionRecordRepository.findByUserId(userId);
    }

    public List<NutritionRecord> getUserNutritionRecordsByDate(Long userId, LocalDate date) {
        return nutritionRecordRepository.findByUserIdAndRecordDate(userId, date);
    }

    public NutritionRecord getNutritionRecord(Long recordId) {
        return nutritionRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("营养记录不存在"));
    }

    @Transactional
    public NutritionRecord createNutritionRecord(Long userId, NutritionRecord record) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        record.setUser(user);
        if (record.getRecordDate() == null) {
            record.setRecordDate(LocalDate.now());
        }
        return nutritionRecordRepository.save(record);
    }

    @Transactional
    public NutritionRecord recognizeFoodFromImage(Long userId, MultipartFile imageFile) {
        try {
            // 先调用AI服务识别食物（直接使用MultipartFile）
            NutritionRecord record = aiService.recognizeFoodFromImage(imageFile);
            
            // 保存图片
            String imageUrl = fileService.saveImage(imageFile);
            
            record.setUser(userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在")));
            record.setImageUrl(imageUrl);
            record.setIsAiRecognized(true);
            record.setRecordDate(LocalDate.now());
            
            return nutritionRecordRepository.save(record);
        } catch (Exception e) {
            throw new RuntimeException("食物识别失败: " + e.getMessage());
        }
    }

    @Transactional
    public NutritionRecord updateNutritionRecord(Long recordId, NutritionRecord record) {
        NutritionRecord existingRecord = nutritionRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("营养记录不存在"));
        
        if (record.getFoodName() != null) existingRecord.setFoodName(record.getFoodName());
        if (record.getQuantity() != null) existingRecord.setQuantity(record.getQuantity());
        if (record.getUnit() != null) existingRecord.setUnit(record.getUnit());
        if (record.getCalories() != null) existingRecord.setCalories(record.getCalories());
        if (record.getProtein() != null) existingRecord.setProtein(record.getProtein());
        if (record.getCarbs() != null) existingRecord.setCarbs(record.getCarbs());
        if (record.getFat() != null) existingRecord.setFat(record.getFat());
        if (record.getNotes() != null) existingRecord.setNotes(record.getNotes());
        
        return nutritionRecordRepository.save(existingRecord);
    }

    @Transactional
    public void deleteNutritionRecord(Long recordId) {
        nutritionRecordRepository.deleteById(recordId);
    }

    public Integer getDailyCalories(Long userId, LocalDate date) {
        Integer total = nutritionRecordRepository.sumCaloriesByUserIdAndDate(userId, date);
        return total != null ? total : 0;
    }

    /**
     * 获取每日详细营养统计
     */
    public Map<String, Object> getDailyNutritionSummary(Long userId, LocalDate date) {
        List<NutritionRecord> records = nutritionRecordRepository.findByUserIdAndRecordDate(userId, date);
        
        Map<String, Object> summary = new HashMap<>();
        int totalCalories = 0;
        double totalProtein = 0.0;
        double totalCarbs = 0.0;
        double totalFat = 0.0;
        double totalFiber = 0.0;
        
        for (NutritionRecord record : records) {
            if (record.getCalories() != null) {
                totalCalories += record.getCalories();
            }
            if (record.getProtein() != null) {
                totalProtein += record.getProtein();
            }
            if (record.getCarbs() != null) {
                totalCarbs += record.getCarbs();
            }
            if (record.getFat() != null) {
                totalFat += record.getFat();
            }
            if (record.getFiber() != null) {
                totalFiber += record.getFiber();
            }
        }
        
        summary.put("calories", totalCalories);
        summary.put("protein", totalProtein);
        summary.put("carbs", totalCarbs);
        summary.put("fat", totalFat);
        summary.put("fiber", totalFiber);
        summary.put("recordCount", records.size());
        
        // 获取目标卡路里并计算预警
        HealthProfile profile = healthProfileService.getHealthProfile(userId);
        if (profile != null && profile.getTargetCalories() != null) {
            Integer targetCalories = profile.getTargetCalories();
            summary.put("targetCalories", targetCalories);
            summary.put("remainingCalories", Math.max(0, targetCalories - totalCalories));
            summary.put("isOverLimit", totalCalories > targetCalories);
            summary.put("overLimitAmount", Math.max(0, totalCalories - targetCalories));
            
            // 计算完成百分比
            double completionPercentage = targetCalories > 0 ? 
                Math.min(100.0, (totalCalories * 100.0 / targetCalories)) : 0.0;
            summary.put("completionPercentage", completionPercentage);
        } else {
            summary.put("targetCalories", null);
            summary.put("remainingCalories", null);
            summary.put("isOverLimit", false);
            summary.put("overLimitAmount", 0);
            summary.put("completionPercentage", 0.0);
        }
        
        return summary;
    }

    /**
     * 获取个性化饮食建议
     */
    public String getPersonalizedNutritionAdvice(Long userId, LocalDate date) {
        HealthProfile profile = healthProfileService.getHealthProfile(userId);
        Map<String, Object> summary = getDailyNutritionSummary(userId, date);
        
        Integer dailyCalories = (Integer) summary.get("calories");
        Integer targetCalories = (Integer) summary.get("targetCalories");
        Double totalProtein = (Double) summary.get("protein");
        Double totalCarbs = (Double) summary.get("carbs");
        Double totalFat = (Double) summary.get("fat");
        
        return aiService.getNutritionAdvice(userId, profile, dailyCalories, targetCalories,
                totalProtein, totalCarbs, totalFat);
    }
}

