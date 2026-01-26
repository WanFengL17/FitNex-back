package com.fitnex.service;

import com.fitnex.dto.WorkoutPlanSummaryDto;
import com.fitnex.entity.HealthProfile;
import com.fitnex.entity.User;
import com.fitnex.entity.WorkoutPlan;
import com.fitnex.entity.WorkoutPlanItem;
import com.fitnex.entity.WorkoutRecord;
import com.fitnex.repository.HealthProfileRepository;
import com.fitnex.repository.UserRepository;
import com.fitnex.repository.WorkoutPlanRepository;
import com.fitnex.repository.WorkoutRecordRepository;
import com.fitnex.service.ai.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkoutPlanService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final UserRepository userRepository;
    private final HealthProfileRepository healthProfileRepository;
    private final AIService aiService;

    public List<WorkoutPlan> getUserWorkoutPlans(Long userId) {
        return workoutPlanRepository.findByUserId(userId);
    }

    public WorkoutPlan getWorkoutPlan(Long planId) {
        return workoutPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("训练计划不存在"));
    }

    @Transactional
    public WorkoutPlan createWorkoutPlan(Long userId, WorkoutPlan plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        plan.setUser(user);
        bindPlanItems(plan);
        return workoutPlanRepository.save(plan);
    }

    @Transactional
    public WorkoutPlan generateAIWorkoutPlan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        HealthProfile profile = healthProfileRepository.findByUserId(userId).orElse(null);

        // 调用AI服务生成训练计划（如果AI不可用则退化为基于用户画像的本地策略）
        WorkoutPlan plan = aiService.generateWorkoutPlan(user, profile);
        plan.setUser(user);
        plan.setIsAiGenerated(true);

        enrichPlanWithProfile(plan, profile);
        bindPlanItems(plan);

        return workoutPlanRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public List<WorkoutPlanSummaryDto> getWorkoutPlanHistory(Long userId) {
        List<WorkoutPlan> plans = workoutPlanRepository.findByUserId(userId);
        Map<Long, List<WorkoutRecord>> recordsByPlan = workoutRecordRepository.findByUserId(userId).stream()
                .filter(r -> r.getWorkoutPlan() != null)
                .collect(Collectors.groupingBy(r -> r.getWorkoutPlan().getId()));

        LocalDateTime fourteenDaysAgo = LocalDateTime.now().minusDays(14);

        return plans.stream()
                .sorted(Comparator.comparing(WorkoutPlan::getCreatedAt))
                .map(plan -> {
                    List<WorkoutRecord> planRecords = recordsByPlan.getOrDefault(plan.getId(), new ArrayList<>());
                    int totalWorkouts = planRecords.size();
                    int totalCalories = planRecords.stream()
                            .mapToInt(r -> r.getCaloriesBurned() != null ? r.getCaloriesBurned() : 0)
                            .sum();
                    long recentCompleted = planRecords.stream()
                            .filter(r -> r.getStartTime() != null && r.getStartTime().isAfter(fourteenDaysAgo))
                            .count();
                    double adherenceRate = calculateAdherence(plan, recentCompleted);

                    String progressTrend = adherenceRate >= 0.8 ? "表现优" :
                            adherenceRate >= 0.5 ? "表现稳定" : "需要提升";

                    return WorkoutPlanSummaryDto.builder()
                            .id(plan.getId())
                            .name(plan.getName())
                            .goal(plan.getGoal())
                            .duration(plan.getDuration())
                            .frequency(plan.getFrequency())
                            .difficulty(plan.getDifficulty())
                            .isActive(plan.getIsActive())
                            .isAiGenerated(plan.getIsAiGenerated())
                            .createdAt(plan.getCreatedAt())
                            .updatedAt(plan.getUpdatedAt())
                            .totalWorkouts(totalWorkouts)
                            .totalCalories(totalCalories)
                            .adherenceRate(adherenceRate)
                            .progressTrend(progressTrend)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkoutPlan adjustWorkoutPlan(Long userId, Long planId, String feedback) {
        WorkoutPlan plan = workoutPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("训练计划不存在"));
        if (!Objects.equals(plan.getUser().getId(), userId)) {
            throw new RuntimeException("无权调整该训练计划");
        }

        LocalDateTime fourteenDaysAgo = LocalDateTime.now().minusDays(14);
        List<WorkoutRecord> recentRecords = workoutRecordRepository
                .findByWorkoutPlanIdAndStartTimeBetween(planId, fourteenDaysAgo, LocalDateTime.now());

        double adherenceRate = calculateAdherence(plan, recentRecords.size());
        tweakPlanDifficulty(plan, adherenceRate, feedback);
        adjustPlanItems(plan, adherenceRate);

        plan.setDescription(buildAdjustmentDescription(plan.getDescription(), adherenceRate, feedback));
        bindPlanItems(plan);
        return workoutPlanRepository.save(plan);
    }

    @Transactional
    public WorkoutPlan updateWorkoutPlan(Long planId, WorkoutPlan plan) {
        WorkoutPlan existingPlan = workoutPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("训练计划不存在"));

        if (plan.getName() != null) existingPlan.setName(plan.getName());
        if (plan.getDescription() != null) existingPlan.setDescription(plan.getDescription());
        if (plan.getGoal() != null) existingPlan.setGoal(plan.getGoal());
        if (plan.getDuration() != null) existingPlan.setDuration(plan.getDuration());
        if (plan.getFrequency() != null) existingPlan.setFrequency(plan.getFrequency());
        if (plan.getDifficulty() != null) existingPlan.setDifficulty(plan.getDifficulty());
        if (plan.getIsActive() != null) existingPlan.setIsActive(plan.getIsActive());

        bindPlanItems(existingPlan);
        return workoutPlanRepository.save(existingPlan);
    }

    @Transactional
    public void deleteWorkoutPlan(Long planId) {
        // 先加载实体，这样 JPA 会处理级联删除
        WorkoutPlan plan = workoutPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("训练计划不存在"));
        workoutPlanRepository.delete(plan);
    }

    private void bindPlanItems(WorkoutPlan plan) {
        if (plan.getPlanItems() == null) {
            return;
        }
        int order = 1;
        for (WorkoutPlanItem item : plan.getPlanItems()) {
            item.setWorkoutPlan(plan);
            if (item.getOrderIndex() == null) {
                item.setOrderIndex(order++);
            }
        }
    }

    private void enrichPlanWithProfile(WorkoutPlan plan, HealthProfile profile) {
        if (profile == null) {
            return;
        }
        // 设置基础元数据
        plan.setGoal(profile.getFitnessGoal() != null ? profile.getFitnessGoal() : plan.getGoal());
        plan.setDifficulty(plan.getDifficulty() != null ? plan.getDifficulty() : deriveDifficulty(profile));
        plan.setFrequency(plan.getFrequency() != null ? plan.getFrequency() : deriveFrequency(profile));
        plan.setDuration(plan.getDuration() != null ? plan.getDuration() : 28);

        // 如果AI没有生成具体项目，基于目标添加默认项目
        if (plan.getPlanItems() == null || plan.getPlanItems().isEmpty()) {
            plan.setPlanItems(defaultPlanItemsForGoal(plan.getGoal(), plan.getFrequency()));
        }
    }

    private List<WorkoutPlanItem> defaultPlanItemsForGoal(String goal, Integer frequency) {
        List<WorkoutPlanItem> items = new ArrayList<>();
        int sessionsPerWeek = frequency != null && frequency > 0 ? frequency : 3;
        for (int i = 1; i <= sessionsPerWeek; i++) {
            WorkoutPlanItem item = new WorkoutPlanItem();
            item.setDayOfWeek(i);
            item.setOrderIndex(i);

            switch (goal == null ? "" : goal) {
                case "减脂" -> {
                    item.setExerciseName("间歇有氧 + 核心训练");
                    item.setExerciseType("有氧");
                    item.setDuration(30);
                    item.setRestTime(60);
                }
                case "增肌" -> {
                    item.setExerciseName("全身力量分化");
                    item.setExerciseType("力量");
                    item.setSets(4);
                    item.setReps(10);
                    item.setRestTime(90);
                }
                default -> {
                    item.setExerciseName("综合体能训练");
                    item.setExerciseType("混合");
                    item.setSets(3);
                    item.setReps(12);
                    item.setRestTime(75);
                }
            }
            items.add(item);
        }
        return items;
    }

    private String deriveDifficulty(HealthProfile profile) {
        String activity = profile.getActivityLevel() == null ? "" : profile.getActivityLevel();
        return switch (activity) {
            case "高度", "极高" -> "高级";
            case "中度" -> "中级";
            default -> "初级";
        };
    }

    private int deriveFrequency(HealthProfile profile) {
        String activity = profile.getActivityLevel() == null ? "" : profile.getActivityLevel();
        return switch (activity) {
            case "高度", "极高" -> 5;
            case "中度" -> 4;
            case "轻度" -> 3;
            default -> 2;
        };
    }

    private double calculateAdherence(WorkoutPlan plan, long completedSessions) {
        int weeklyTarget = plan.getFrequency() != null ? plan.getFrequency() : 3;
        // 14天窗口内的完成度
        double targetInWindow = weeklyTarget * 2.0;
        if (targetInWindow == 0) {
            return 1.0;
        }
        double adherence = completedSessions / targetInWindow;
        return Math.min(1.0, Math.round(adherence * 100.0) / 100.0);
    }

    private void tweakPlanDifficulty(WorkoutPlan plan, double adherenceRate, String feedback) {
        // 根据完成度和反馈动态调整强度和频次
        if (adherenceRate >= 0.85) {
            plan.setDifficulty("高级");
            plan.setFrequency((plan.getFrequency() != null ? plan.getFrequency() : 3) + 1);
        } else if (adherenceRate >= 0.6) {
            plan.setDifficulty("中级");
        } else {
            plan.setDifficulty("初级");
            plan.setFrequency(Math.max(2, (plan.getFrequency() != null ? plan.getFrequency() : 3) - 1));
        }

        if (feedback != null && feedback.contains("疲劳")) {
            plan.setDifficulty("初级");
        }
    }

    private void adjustPlanItems(WorkoutPlan plan, double adherenceRate) {
        if (plan.getPlanItems() == null) {
            return;
        }
        for (WorkoutPlanItem item : plan.getPlanItems()) {
            if (item.getSets() != null) {
                int delta = adherenceRate >= 0.85 ? 1 : adherenceRate < 0.5 ? -1 : 0;
                item.setSets(Math.max(2, item.getSets() + delta));
            }
            if (item.getReps() != null) {
                int delta = adherenceRate >= 0.85 ? 2 : adherenceRate < 0.5 ? -2 : 0;
                item.setReps(Math.max(8, item.getReps() + delta));
            }
            if (item.getDuration() != null) {
                int delta = adherenceRate >= 0.85 ? 5 : adherenceRate < 0.5 ? -5 : 0;
                item.setDuration(Math.max(15, item.getDuration() + delta));
            }
        }
    }

    private String buildAdjustmentDescription(String description, double adherenceRate, String feedback) {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) {
            sb.append(description).append(" | ");
        }
        sb.append("最近完成度: ").append((int) (adherenceRate * 100)).append("%");
        if (feedback != null && !feedback.isBlank()) {
            sb.append(" | 反馈: ").append(feedback);
        }
        sb.append(" | 已根据进度自动优化");
        return sb.toString();
    }
}

