package com.fitnex.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workout_plan_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    @JsonBackReference
    private WorkoutPlan workoutPlan;

    private Integer dayOfWeek; // 1-7 表示周一到周日
    private String exerciseName; // 运动名称
    private String exerciseType; // 运动类型：有氧、力量、柔韧性等
    private Integer sets; // 组数
    private Integer reps; // 次数
    private Double weight; // 重量（公斤）
    private Integer duration; // 时长（秒）
    private Integer restTime; // 休息时间（秒）
    private String instructions; // 动作说明
    private String videoUrl; // 视频链接
    private Integer orderIndex; // 顺序
}

