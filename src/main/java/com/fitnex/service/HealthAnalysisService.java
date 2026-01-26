package com.fitnex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitnex.entity.*;
import com.fitnex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthAnalysisService {

    private final HealthAnalysisRepository healthAnalysisRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final NutritionRecordRepository nutritionRecordRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final HealthProfileRepository healthProfileRepository;
    private final UserRepository userRepository;

    public HealthAnalysis getLatestAnalysis(Long userId) {
        List<HealthAnalysis> analyses = healthAnalysisRepository.findByUserIdOrderByAnalysisDateDesc(userId);
        return analyses.isEmpty() ? null : analyses.get(0);
    }

    @Transactional
    public HealthAnalysis generateHealthAnalysis(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);

        // 获取训练数据（包含exerciseRecords）
        List<WorkoutRecord> workouts = workoutRecordRepository.findByUserIdAndStartTimeBetweenWithExercises(
                userId, thirtyDaysAgo.atStartOfDay(), LocalDateTime.now());
        
        // 获取营养数据
        List<NutritionRecord> nutritionRecords = nutritionRecordRepository.findByUserIdAndRecordDateBetween(
                userId, thirtyDaysAgo, today);
        
        // 获取身体测量数据
        List<BodyMeasurement> measurements = bodyMeasurementRepository.findByUserIdAndMeasureDateBetween(
                userId, thirtyDaysAgo, today);
        
        // 获取健康档案
        HealthProfile profile = healthProfileRepository.findByUserId(userId).orElse(null);

        // 计算进度评分
        double progressScore = calculateProgressScore(workouts, nutritionRecords, measurements, profile);

        // 计算体重变化
        Double weightChange = calculateWeightChange(measurements);

        // 计算体脂率变化
        Double bodyFatChange = calculateBodyFatChange(measurements);

        // 生成风险预警
        String riskWarnings = generateRiskWarnings(workouts, nutritionRecords, measurements, profile);

        // 生成建议
        String recommendations = generateRecommendations(workouts, nutritionRecords, measurements, profile);

        // 生成图表数据
        String analysisData = generateAnalysisData(workouts, nutritionRecords, measurements);

        HealthAnalysis analysis = new HealthAnalysis();
        analysis.setUser(user);
        analysis.setAnalysisDate(today);
        analysis.setProgressScore(progressScore);
        analysis.setProgressLevel(getProgressLevel(progressScore));
        analysis.setTotalWorkouts(workouts.size());
        analysis.setTotalCaloriesBurned(workouts.stream()
                .mapToInt(w -> w.getCaloriesBurned() != null ? w.getCaloriesBurned() : 0)
                .sum());
        analysis.setWeightChange(weightChange);
        analysis.setBodyFatChange(bodyFatChange);
        analysis.setRiskWarnings(riskWarnings);
        analysis.setRecommendations(recommendations);
        analysis.setAnalysisData(analysisData);

        return healthAnalysisRepository.save(analysis);
    }

    private double calculateProgressScore(List<WorkoutRecord> workouts, 
                                         List<NutritionRecord> nutritionRecords,
                                         List<BodyMeasurement> measurements,
                                         HealthProfile profile) {
        double score = 0.0;
        int factors = 0;

        // 训练频率评分 (40%)
        if (!workouts.isEmpty()) {
            double workoutFrequency = workouts.size() / 30.0; // 30天内训练次数
            double workoutScore = Math.min(workoutFrequency * 10, 40); // 最多40分
            score += workoutScore;
            factors++;
        }

        // 营养记录评分 (30%)
        if (!nutritionRecords.isEmpty()) {
            double nutritionScore = Math.min(nutritionRecords.size() / 90.0 * 30, 30); // 最多30分
            score += nutritionScore;
            factors++;
        }

        // 身体测量评分 (30%)
        if (!measurements.isEmpty() && profile != null && profile.getTargetWeight() != null) {
            BodyMeasurement latest = measurements.get(measurements.size() - 1);
            if (latest.getWeight() != null) {
                double weightProgress = 1 - Math.abs(latest.getWeight() - profile.getTargetWeight()) / profile.getTargetWeight();
                double measurementScore = Math.max(0, weightProgress * 30);
                score += measurementScore;
                factors++;
            }
        }

        return factors > 0 ? score : 0.0;
    }

    private Double calculateWeightChange(List<BodyMeasurement> measurements) {
        if (measurements.size() < 2) return null;
        BodyMeasurement first = measurements.get(0);
        BodyMeasurement last = measurements.get(measurements.size() - 1);
        if (first.getWeight() != null && last.getWeight() != null) {
            return last.getWeight() - first.getWeight();
        }
        return null;
    }

    private Double calculateBodyFatChange(List<BodyMeasurement> measurements) {
        if (measurements.size() < 2) return null;
        BodyMeasurement first = measurements.get(0);
        BodyMeasurement last = measurements.get(measurements.size() - 1);
        if (first.getBodyFat() != null && last.getBodyFat() != null) {
            return last.getBodyFat() - first.getBodyFat();
        }
        return null;
    }

    private String generateRiskWarnings(List<WorkoutRecord> workouts,
                                        List<NutritionRecord> nutritionRecords,
                                        List<BodyMeasurement> measurements,
                                        HealthProfile profile) {
        Map<String, String> warnings = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 训练频率过低
        if (workouts.size() < 8) {
            warnings.put("训练频率", "过去30天训练次数较少，建议增加训练频率");
        }

        // 营养记录不足
        if (nutritionRecords.size() < 30) {
            warnings.put("营养记录", "营养记录不完整，建议坚持记录饮食");
        }

        // 体重异常变化（30天内变化超过5kg）
        Double weightChange = calculateWeightChange(measurements);
        if (weightChange != null && Math.abs(weightChange) > 5) {
            warnings.put("体重变化", String.format("体重在30天内变化%.2fkg，变化较大，请关注健康状况", weightChange));
        }
        
        // 体重骤降（7天内下降超过2kg）
        if (measurements.size() >= 2) {
            LocalDate today = LocalDate.now();
            LocalDate sevenDaysAgo = today.minusDays(7);
            
            BodyMeasurement recent = measurements.stream()
                    .filter(m -> m.getMeasureDate().isAfter(sevenDaysAgo.minusDays(1)))
                    .max(Comparator.comparing(BodyMeasurement::getMeasureDate))
                    .orElse(null);
            
            BodyMeasurement previous = measurements.stream()
                    .filter(m -> m.getMeasureDate().isBefore(sevenDaysAgo.plusDays(1)))
                    .max(Comparator.comparing(BodyMeasurement::getMeasureDate))
                    .orElse(null);
            
            if (recent != null && previous != null 
                    && recent.getWeight() != null && previous.getWeight() != null) {
                double weekWeightChange = recent.getWeight() - previous.getWeight();
                if (weekWeightChange < -2) {
                    warnings.put("体重骤降", String.format("7天内体重下降%.2fkg，下降过快，请关注健康状况", Math.abs(weekWeightChange)));
                }
            }
        }

        // 心率过高预警
        for (WorkoutRecord workout : workouts) {
            if (workout.getMaxHeartRate() != null && profile != null && profile.getBirthDate() != null) {
                // 计算年龄
                int age = Period.between(profile.getBirthDate(), LocalDate.now()).getYears();
                // 计算最大心率（220 - 年龄）
                int maxHeartRate = 220 - age;
                if (workout.getMaxHeartRate() > maxHeartRate * 0.95) {
                    warnings.put("心率过高", String.format("训练时最大心率达到%.0f次/分，接近安全上限，请注意训练强度", workout.getMaxHeartRate()));
                    break;
                }
            }
        }
        
        // 平均心率持续偏高
        double avgMaxHeartRate = workouts.stream()
                .filter(w -> w.getMaxHeartRate() != null)
                .mapToDouble(WorkoutRecord::getMaxHeartRate)
                .average()
                .orElse(0);
        
        if (avgMaxHeartRate > 0 && profile != null && profile.getBirthDate() != null) {
            // 计算年龄
            int age = Period.between(profile.getBirthDate(), LocalDate.now()).getYears();
            // 计算最大心率（220 - 年龄）
            int maxHeartRate = 220 - age;
            if (avgMaxHeartRate > maxHeartRate * 0.90) {
                warnings.put("心率持续偏高", "训练时平均最大心率偏高，建议适当降低训练强度");
            }
        }

        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JsonProcessingException e) {
            return warnings.toString();
        }
    }

    private String generateRecommendations(List<WorkoutRecord> workouts,
                                          List<NutritionRecord> nutritionRecords,
                                          List<BodyMeasurement> measurements,
                                          HealthProfile profile) {
        Map<String, String> recommendations = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        
        if (workouts.size() < 12) {
            recommendations.put("训练", "建议每周至少训练3-4次");
        }
        
        if (nutritionRecords.size() < 60) {
            recommendations.put("营养", "建议每天记录三餐饮食");
        }
        
        // 根据体重变化给出建议
        Double weightChange = calculateWeightChange(measurements);
        if (weightChange != null && profile != null && profile.getTargetWeight() != null) {
            double currentWeight = measurements.isEmpty() ? 0 : 
                (measurements.get(measurements.size() - 1).getWeight() != null ? 
                 measurements.get(measurements.size() - 1).getWeight() : 0);
            double targetWeight = profile.getTargetWeight();
            if (currentWeight > targetWeight && weightChange > 0) {
                recommendations.put("减重", "体重仍在上升，建议增加有氧运动和控制饮食");
            } else if (currentWeight < targetWeight && weightChange < 0) {
                recommendations.put("增重", "体重下降，建议增加力量训练和营养摄入");
            }
        }

        try {
            return objectMapper.writeValueAsString(recommendations);
        } catch (JsonProcessingException e) {
            return recommendations.toString();
        }
    }

    private String generateAnalysisData(List<WorkoutRecord> workouts,
                                       List<NutritionRecord> nutritionRecords,
                                       List<BodyMeasurement> measurements) {
        Map<String, Object> data = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 训练热力图数据（按日期）
        Map<String, Integer> workoutHeatmap = new HashMap<>();
        for (WorkoutRecord workout : workouts) {
            String date = workout.getStartTime().toLocalDate().toString();
            workoutHeatmap.put(date, workout.getCaloriesBurned() != null ? workout.getCaloriesBurned() : 0);
        }
        data.put("workoutHeatmap", workoutHeatmap);

        // 身体部位训练热力图数据
        Map<String, Object> bodyPartHeatmap = generateBodyPartHeatmap(workouts);
        data.put("bodyPartHeatmap", bodyPartHeatmap);

        // 体重曲线数据
        List<Map<String, Object>> weightCurve = new ArrayList<>();
        measurements.stream()
                .filter(m -> m.getWeight() != null)
                .sorted(Comparator.comparing(BodyMeasurement::getMeasureDate))
                .forEach(measurement -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("date", measurement.getMeasureDate().toString());
                    point.put("weight", measurement.getWeight());
                    weightCurve.add(point);
                });
        data.put("weightCurve", weightCurve);

        // 进度追踪数据
        Map<String, Object> progressTracking = generateProgressTracking(workouts, measurements);
        data.put("progressTracking", progressTracking);

        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }

    private Map<String, Object> generateBodyPartHeatmap(List<WorkoutRecord> workouts) {
        Map<String, Integer> bodyPartFrequency = new HashMap<>();
        Map<String, Double> bodyPartIntensity = new HashMap<>();
        
        // 身体部位映射（根据动作名称推断）
        Map<String, List<String>> exerciseToBodyParts = new HashMap<>();
        exerciseToBodyParts.put("胸", Arrays.asList("胸部"));
        exerciseToBodyParts.put("背", Arrays.asList("背部"));
        exerciseToBodyParts.put("肩", Arrays.asList("肩部"));
        exerciseToBodyParts.put("腿", Arrays.asList("腿部"));
        exerciseToBodyParts.put("臂", Arrays.asList("手臂"));
        exerciseToBodyParts.put("腹", Arrays.asList("腹部"));
        exerciseToBodyParts.put("核心", Arrays.asList("核心"));
        
        // 常见动作到身体部位的映射
        Map<String, String> exerciseNameToBodyPart = new HashMap<>();
        exerciseNameToBodyPart.put("卧推", "胸部");
        exerciseNameToBodyPart.put("深蹲", "腿部");
        exerciseNameToBodyPart.put("硬拉", "背部");
        exerciseNameToBodyPart.put("引体向上", "背部");
        exerciseNameToBodyPart.put("俯卧撑", "胸部");
        exerciseNameToBodyPart.put("卷腹", "腹部");
        exerciseNameToBodyPart.put("平板支撑", "核心");
        exerciseNameToBodyPart.put("推举", "肩部");
        exerciseNameToBodyPart.put("弯举", "手臂");
        exerciseNameToBodyPart.put("臂屈伸", "手臂");
        exerciseNameToBodyPart.put("划船", "背部");
        exerciseNameToBodyPart.put("飞鸟", "胸部");
        exerciseNameToBodyPart.put("腿举", "腿部");
        exerciseNameToBodyPart.put("腿弯举", "腿部");
        exerciseNameToBodyPart.put("提踵", "腿部");
        
        for (WorkoutRecord workout : workouts) {
            if (workout.getExerciseRecords() != null) {
                for (WorkoutExerciseRecord exercise : workout.getExerciseRecords()) {
                    String exerciseName = exercise.getExerciseName();
                    if (exerciseName == null) continue;
                    
                    String bodyPart = null;
                    // 精确匹配
                    for (Map.Entry<String, String> entry : exerciseNameToBodyPart.entrySet()) {
                        if (exerciseName.contains(entry.getKey())) {
                            bodyPart = entry.getValue();
                            break;
                        }
                    }
                    
                    // 模糊匹配
                    if (bodyPart == null) {
                        for (Map.Entry<String, List<String>> entry : exerciseToBodyParts.entrySet()) {
                            if (exerciseName.contains(entry.getKey())) {
                                bodyPart = entry.getValue().get(0);
                                break;
                            }
                        }
                    }
                    
                    if (bodyPart == null) {
                        bodyPart = "其他";
                    }
                    
                    // 统计频次
                    bodyPartFrequency.put(bodyPart, bodyPartFrequency.getOrDefault(bodyPart, 0) + 1);
                    
                    // 计算强度（基于重量、组数、次数）
                    double intensity = 0;
                    if (exercise.getWeight() != null && exercise.getSets() != null && exercise.getReps() != null) {
                        intensity = exercise.getWeight() * exercise.getSets() * exercise.getReps();
                    } else if (exercise.getDuration() != null) {
                        intensity = exercise.getDuration() / 60.0; // 转换为分钟
                    }
                    
                    bodyPartIntensity.put(bodyPart, bodyPartIntensity.getOrDefault(bodyPart, 0.0) + intensity);
                }
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("frequency", bodyPartFrequency);
        result.put("intensity", bodyPartIntensity);
        return result;
    }

    private Map<String, Object> generateProgressTracking(List<WorkoutRecord> workouts,
                                                         List<BodyMeasurement> measurements) {
        Map<String, Object> progress = new HashMap<>();
        
        // 训练次数对比（最近7天 vs 前7天）
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);
        LocalDate fourteenDaysAgo = today.minusDays(14);
        
        long recentWorkouts = workouts.stream()
                .filter(w -> w.getStartTime().toLocalDate().isAfter(sevenDaysAgo.minusDays(1)))
                .count();
        
        long previousWorkouts = workouts.stream()
                .filter(w -> {
                    LocalDate workoutDate = w.getStartTime().toLocalDate();
                    return workoutDate.isAfter(fourteenDaysAgo.minusDays(1)) && workoutDate.isBefore(sevenDaysAgo.plusDays(1));
                })
                .count();
        
        progress.put("recentWorkoutCount", recentWorkouts);
        progress.put("previousWorkoutCount", previousWorkouts);
        progress.put("workoutChange", recentWorkouts - previousWorkouts);
        
        // 卡路里消耗对比
        int recentCalories = workouts.stream()
                .filter(w -> w.getStartTime().toLocalDate().isAfter(sevenDaysAgo.minusDays(1)))
                .mapToInt(w -> w.getCaloriesBurned() != null ? w.getCaloriesBurned() : 0)
                .sum();
        
        int previousCalories = workouts.stream()
                .filter(w -> {
                    LocalDate workoutDate = w.getStartTime().toLocalDate();
                    return workoutDate.isAfter(fourteenDaysAgo.minusDays(1)) && workoutDate.isBefore(sevenDaysAgo.plusDays(1));
                })
                .mapToInt(w -> w.getCaloriesBurned() != null ? w.getCaloriesBurned() : 0)
                .sum();
        
        progress.put("recentCalories", recentCalories);
        progress.put("previousCalories", previousCalories);
        progress.put("caloriesChange", recentCalories - previousCalories);
        
        // 体重变化对比
        if (measurements.size() >= 2) {
            BodyMeasurement recentMeasurement = measurements.stream()
                    .filter(m -> m.getMeasureDate().isAfter(sevenDaysAgo.minusDays(1)))
                    .max(Comparator.comparing(BodyMeasurement::getMeasureDate))
                    .orElse(null);
            
            BodyMeasurement previousMeasurement = measurements.stream()
                    .filter(m -> {
                        LocalDate measureDate = m.getMeasureDate();
                        return measureDate.isAfter(fourteenDaysAgo.minusDays(1)) && measureDate.isBefore(sevenDaysAgo.plusDays(1));
                    })
                    .max(Comparator.comparing(BodyMeasurement::getMeasureDate))
                    .orElse(null);
            
            if (recentMeasurement != null && previousMeasurement != null 
                    && recentMeasurement.getWeight() != null && previousMeasurement.getWeight() != null) {
                progress.put("recentWeight", recentMeasurement.getWeight());
                progress.put("previousWeight", previousMeasurement.getWeight());
                progress.put("weightChange", recentMeasurement.getWeight() - previousMeasurement.getWeight());
            }
        }
        
        return progress;
    }

    private String getProgressLevel(double score) {
        if (score >= 80) return "优秀";
        if (score >= 60) return "良好";
        if (score >= 40) return "一般";
        return "需改进";
    }

    public Map<String, Object> getWeightHistory(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);
        
        List<BodyMeasurement> measurements = bodyMeasurementRepository
                .findByUserIdAndMeasureDateBetween(userId, startDate, today);
        
        List<Map<String, Object>> weightData = new ArrayList<>();
        measurements.stream()
                .filter(m -> m.getWeight() != null)
                .sorted(Comparator.comparing(BodyMeasurement::getMeasureDate))
                .forEach(measurement -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("date", measurement.getMeasureDate().toString());
                    point.put("weight", measurement.getWeight());
                    if (measurement.getBodyFat() != null) {
                        point.put("bodyFat", measurement.getBodyFat());
                    }
                    weightData.add(point);
                });
        
        Map<String, Object> result = new HashMap<>();
        result.put("data", weightData);
        result.put("days", days);
        return result;
    }

    public Map<String, Object> getWorkoutHeatmap(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);
        
        List<WorkoutRecord> workouts = workoutRecordRepository.findByUserIdAndStartTimeBetweenWithExercises(
                userId, startDate.atStartOfDay(), LocalDateTime.now());
        
        // 按日期统计训练数据
        Map<String, Integer> dateHeatmap = new HashMap<>();
        for (WorkoutRecord workout : workouts) {
            String date = workout.getStartTime().toLocalDate().toString();
            int calories = workout.getCaloriesBurned() != null ? workout.getCaloriesBurned() : 0;
            dateHeatmap.put(date, dateHeatmap.getOrDefault(date, 0) + calories);
        }
        
        // 身体部位热力图
        Map<String, Object> bodyPartHeatmap = generateBodyPartHeatmap(workouts);
        
        Map<String, Object> result = new HashMap<>();
        result.put("dateHeatmap", dateHeatmap);
        result.put("bodyPartHeatmap", bodyPartHeatmap);
        result.put("days", days);
        return result;
    }

    public Map<String, Object> getProgressTracking(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);
        
        List<WorkoutRecord> workouts = workoutRecordRepository.findByUserIdAndStartTimeBetweenWithExercises(
                userId, thirtyDaysAgo.atStartOfDay(), LocalDateTime.now());
        
        List<BodyMeasurement> measurements = bodyMeasurementRepository.findByUserIdAndMeasureDateBetween(
                userId, thirtyDaysAgo, today);
        
        return generateProgressTracking(workouts, measurements);
    }
}

