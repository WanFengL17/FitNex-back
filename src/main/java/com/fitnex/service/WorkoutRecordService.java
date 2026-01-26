package com.fitnex.service;

import com.fitnex.entity.User;
import com.fitnex.entity.WorkoutPlan;
import com.fitnex.entity.WorkoutRecord;
import com.fitnex.entity.WorkoutExerciseRecord;
import com.fitnex.repository.UserRepository;
import com.fitnex.repository.WorkoutPlanRepository;
import com.fitnex.repository.WorkoutRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkoutRecordService {

    private final WorkoutRecordRepository workoutRecordRepository;
    private final UserRepository userRepository;
    private final WorkoutPlanRepository workoutPlanRepository;

    public List<WorkoutRecord> getUserWorkoutRecords(Long userId) {
        return workoutRecordRepository.findByUserId(userId);
    }

    public List<WorkoutRecord> getUserWorkoutRecordsByDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return workoutRecordRepository.findByUserIdAndStartTimeBetween(userId, start, end);
    }

    public WorkoutRecord getWorkoutRecord(Long recordId) {
        return workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
    }

    @Transactional
    public WorkoutRecord createWorkoutRecord(Long userId, WorkoutRecord record) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        record.setUser(user);
        
        if (record.getStartTime() == null) {
            record.setStartTime(LocalDateTime.now());
        }
        if (record.getEndTime() != null && record.getStartTime() != null) {
            long durationSeconds = java.time.Duration.between(record.getStartTime(), record.getEndTime()).getSeconds();
            record.setDuration((int) durationSeconds);
        }
        
        return workoutRecordRepository.save(record);
    }

    @Transactional
    public WorkoutRecord updateWorkoutRecord(Long recordId, WorkoutRecord record) {
        WorkoutRecord existingRecord = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
        
        if (record.getEndTime() != null) {
            existingRecord.setEndTime(record.getEndTime());
            if (existingRecord.getStartTime() != null) {
                long durationSeconds = java.time.Duration.between(existingRecord.getStartTime(), record.getEndTime()).getSeconds();
                existingRecord.setDuration((int) durationSeconds);
            }
        }
        if (record.getCaloriesBurned() != null) existingRecord.setCaloriesBurned(record.getCaloriesBurned());
        if (record.getAverageHeartRate() != null) existingRecord.setAverageHeartRate(record.getAverageHeartRate());
        if (record.getMaxHeartRate() != null) existingRecord.setMaxHeartRate(record.getMaxHeartRate());
        if (record.getMinHeartRate() != null) existingRecord.setMinHeartRate(record.getMinHeartRate());
        if (record.getStatus() != null) existingRecord.setStatus(record.getStatus());
        if (record.getWorkoutName() != null) existingRecord.setWorkoutName(record.getWorkoutName());
        if (record.getNotes() != null) existingRecord.setNotes(record.getNotes());
        
        return workoutRecordRepository.save(existingRecord);
    }

    @Transactional
    public void deleteWorkoutRecord(Long recordId) {
        workoutRecordRepository.deleteById(recordId);
    }

    @Transactional
    public WorkoutRecord startWorkout(Long userId, Long planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        WorkoutPlan plan = null;
        if (planId != null) {
            plan = workoutPlanRepository.findById(planId)
                    .orElseThrow(() -> new RuntimeException("训练计划不存在"));
        }
        
        WorkoutRecord record = new WorkoutRecord();
        record.setUser(user);
        record.setWorkoutPlan(plan);
        record.setStartTime(LocalDateTime.now());
        record.setStatus("IN_PROGRESS");
        record.setWorkoutName(plan != null ? plan.getName() : "自由训练");
        
        // 如果基于计划，初始化训练项目记录
        if (plan != null && plan.getPlanItems() != null) {
            List<WorkoutExerciseRecord> exerciseRecords = plan.getPlanItems().stream()
                    .map(item -> {
                        WorkoutExerciseRecord exerciseRecord = new WorkoutExerciseRecord();
                        exerciseRecord.setWorkoutRecord(record);
                        exerciseRecord.setExerciseName(item.getExerciseName());
                        exerciseRecord.setSets(item.getSets());
                        exerciseRecord.setReps(item.getReps());
                        exerciseRecord.setWeight(item.getWeight());
                        exerciseRecord.setDuration(item.getDuration());
                        exerciseRecord.setRestTime(item.getRestTime());
                        exerciseRecord.setVideoUrl(item.getVideoUrl());
                        exerciseRecord.setInstructions(item.getInstructions());
                        exerciseRecord.setOrderIndex(item.getOrderIndex());
                        exerciseRecord.setIsCompleted(false);
                        exerciseRecord.setCompletedSets(0);
                        exerciseRecord.setCompletedReps(0);
                        return exerciseRecord;
                    })
                    .collect(Collectors.toList());
            record.setExerciseRecords(exerciseRecords);
        }
        
        return workoutRecordRepository.save(record);
    }

    @Transactional
    public WorkoutRecord pauseWorkout(Long recordId) {
        WorkoutRecord record = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
        
        if (!"IN_PROGRESS".equals(record.getStatus())) {
            throw new RuntimeException("只能暂停进行中的训练");
        }
        
        record.setStatus("PAUSED");
        return workoutRecordRepository.save(record);
    }

    @Transactional
    public WorkoutRecord resumeWorkout(Long recordId) {
        WorkoutRecord record = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
        
        if (!"PAUSED".equals(record.getStatus())) {
            throw new RuntimeException("只能恢复已暂停的训练");
        }
        
        record.setStatus("IN_PROGRESS");
        return workoutRecordRepository.save(record);
    }

    @Transactional
    public WorkoutRecord endWorkout(Long recordId) {
        WorkoutRecord record = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
        
        record.setEndTime(LocalDateTime.now());
        record.setStatus("COMPLETED");
        
        if (record.getStartTime() != null) {
            long durationSeconds = java.time.Duration.between(record.getStartTime(), record.getEndTime()).getSeconds();
            record.setDuration((int) durationSeconds);
        }
        
        // 自动计算卡路里（如果未设置）
        if (record.getCaloriesBurned() == null) {
            record.setCaloriesBurned(estimateCaloriesBurned(record));
        }
        
        return workoutRecordRepository.save(record);
    }

    @Transactional
    public WorkoutRecord updateWorkoutProgress(Long recordId, WorkoutRecord progress) {
        WorkoutRecord existingRecord = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
        
        // 更新实时数据
        if (progress.getCaloriesBurned() != null) {
            existingRecord.setCaloriesBurned(progress.getCaloriesBurned());
        }
        if (progress.getAverageHeartRate() != null) {
            existingRecord.setAverageHeartRate(progress.getAverageHeartRate());
        }
        if (progress.getMaxHeartRate() != null) {
            existingRecord.setMaxHeartRate(progress.getMaxHeartRate());
        }
        if (progress.getMinHeartRate() != null) {
            existingRecord.setMinHeartRate(progress.getMinHeartRate());
        }
        
        // 更新训练时长（实时计算）
        if (existingRecord.getStartTime() != null) {
            long durationSeconds = java.time.Duration.between(existingRecord.getStartTime(), LocalDateTime.now()).getSeconds();
            existingRecord.setDuration((int) durationSeconds);
        }
        
        return workoutRecordRepository.save(existingRecord);
    }

    @Transactional
    public WorkoutRecord updateExerciseProgress(Long recordId, Long exerciseId, WorkoutExerciseRecord progress) {
        WorkoutRecord record = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
        
        if (record.getExerciseRecords() == null) {
            throw new RuntimeException("训练记录中没有运动项目");
        }
        
        WorkoutExerciseRecord exerciseRecord = record.getExerciseRecords().stream()
                .filter(e -> e.getId().equals(exerciseId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("运动项目不存在"));
        
        if (progress.getCompletedSets() != null) {
            exerciseRecord.setCompletedSets(progress.getCompletedSets());
        }
        if (progress.getCompletedReps() != null) {
            exerciseRecord.setCompletedReps(progress.getCompletedReps());
        }
        if (progress.getIsCompleted() != null) {
            exerciseRecord.setIsCompleted(progress.getIsCompleted());
        }
        if (progress.getWeight() != null) {
            exerciseRecord.setWeight(progress.getWeight());
        }
        if (progress.getNotes() != null) {
            exerciseRecord.setNotes(progress.getNotes());
        }
        
        return workoutRecordRepository.save(record);
    }

    @Transactional
    public WorkoutRecord shareWorkoutRecord(Long recordId) {
        WorkoutRecord record = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("训练记录不存在"));
        
        if (!record.getIsShared()) {
            record.setIsShared(true);
            record.setShareCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        
        return workoutRecordRepository.save(record);
    }

    public WorkoutRecord getWorkoutRecordByShareCode(String shareCode) {
        List<WorkoutRecord> allRecords = workoutRecordRepository.findAll();
        return allRecords.stream()
                .filter(r -> r.getIsShared() != null && r.getIsShared() && 
                           shareCode.equals(r.getShareCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("分享码无效或已过期"));
    }

    private Integer estimateCaloriesBurned(WorkoutRecord record) {
        // 简单的卡路里估算：基于时长和心率
        int baseCalories = 0;
        if (record.getDuration() != null) {
            // 基础代谢：每分钟约5-10卡路里（取决于强度）
            int minutes = record.getDuration() / 60;
            baseCalories = minutes * 7; // 中等强度估算
            
            // 如果有心率数据，根据心率调整
            if (record.getAverageHeartRate() != null) {
                double heartRateFactor = record.getAverageHeartRate() / 120.0; // 假设120为基准心率
                baseCalories = (int) (baseCalories * heartRateFactor);
            }
        }
        return Math.max(baseCalories, 50); // 最少50卡路里
    }
}

