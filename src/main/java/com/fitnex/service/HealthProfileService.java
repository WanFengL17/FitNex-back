package com.fitnex.service;

import com.fitnex.entity.HealthProfile;
import com.fitnex.entity.User;
import com.fitnex.repository.HealthProfileRepository;
import com.fitnex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class HealthProfileService {

    private final HealthProfileRepository healthProfileRepository;
    private final UserRepository userRepository;

    public HealthProfile getHealthProfile(Long userId) {
        return healthProfileRepository.findByUserId(userId)
                .orElse(null);
    }

    @Transactional
    public HealthProfile createOrUpdateHealthProfile(Long userId, HealthProfile profile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        HealthProfile existingProfile = healthProfileRepository.findByUserId(userId).orElse(null);

        if (existingProfile != null) {
            // 更新现有档案
            if (profile.getBirthDate() != null) existingProfile.setBirthDate(profile.getBirthDate());
            if (profile.getGender() != null) existingProfile.setGender(profile.getGender());
            if (profile.getHeight() != null) existingProfile.setHeight(profile.getHeight());
            if (profile.getWeight() != null) existingProfile.setWeight(profile.getWeight());
            if (profile.getBodyFat() != null) existingProfile.setBodyFat(profile.getBodyFat());
            if (profile.getMuscleMass() != null) existingProfile.setMuscleMass(profile.getMuscleMass());
            if (profile.getFitnessGoal() != null) existingProfile.setFitnessGoal(profile.getFitnessGoal());
            if (profile.getActivityLevel() != null) existingProfile.setActivityLevel(profile.getActivityLevel());
            if (profile.getTargetWeight() != null) existingProfile.setTargetWeight(profile.getTargetWeight());
            if (profile.getMedicalHistory() != null) existingProfile.setMedicalHistory(profile.getMedicalHistory());
            if (profile.getBloodType() != null) existingProfile.setBloodType(profile.getBloodType());
            if (profile.getAllergies() != null) existingProfile.setAllergies(profile.getAllergies());
            if (profile.getDietaryRestrictions() != null) existingProfile.setDietaryRestrictions(profile.getDietaryRestrictions());

            // 计算BMI
            if (existingProfile.getHeight() != null && existingProfile.getWeight() != null) {
                double heightInMeters = existingProfile.getHeight() / 100.0;
                double bmi = existingProfile.getWeight() / (heightInMeters * heightInMeters);
                existingProfile.setBmi(bmi);
            }

            // 计算目标卡路里
            existingProfile.setTargetCalories(calculateTargetCalories(existingProfile));

            return healthProfileRepository.save(existingProfile);
        } else {
            // 创建新档案
            profile.setUser(user);
            if (profile.getHeight() != null && profile.getWeight() != null) {
                double heightInMeters = profile.getHeight() / 100.0;
                double bmi = profile.getWeight() / (heightInMeters * heightInMeters);
                profile.setBmi(bmi);
            }
            profile.setTargetCalories(calculateTargetCalories(profile));
            return healthProfileRepository.save(profile);
        }
    }

    private Integer calculateTargetCalories(HealthProfile profile) {
        if (profile.getWeight() == null || profile.getHeight() == null || profile.getBirthDate() == null) {
            return 2000; // 默认值
        }

        // 使用Mifflin-St Jeor公式计算基础代谢率(BMR)
        double bmr;
        int age = Period.between(profile.getBirthDate(), LocalDate.now()).getYears();
        double weight = profile.getWeight();
        double height = profile.getHeight();

        if ("MALE".equalsIgnoreCase(profile.getGender())) {
            bmr = 10 * weight + 6.25 * height - 5 * age + 5;
        } else {
            bmr = 10 * weight + 6.25 * height - 5 * age - 161;
        }

        // 根据活动水平计算总消耗
        double activityMultiplier = switch (profile.getActivityLevel()) {
            case "久坐" -> 1.2;
            case "轻度" -> 1.375;
            case "中度" -> 1.55;
            case "高度" -> 1.725;
            case "极高" -> 1.9;
            default -> 1.375;
        };

        double tdee = bmr * activityMultiplier;

        // 根据健身目标调整
        if ("减脂".equals(profile.getFitnessGoal())) {
            tdee = tdee * 0.85; // 减少15%卡路里
        } else if ("增肌".equals(profile.getFitnessGoal())) {
            tdee = tdee * 1.15; // 增加15%卡路里
        }

        return (int) Math.round(tdee);
    }
}

