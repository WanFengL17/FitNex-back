package com.fitnex.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workout_exercise_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutExerciseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workout_record_id", nullable = false)
    @JsonBackReference
    private WorkoutRecord workoutRecord;

    private String exerciseName;
    private Integer sets;
    private Integer reps;
    private Double weight;
    private Integer duration;
    private Integer restTime;
    private Integer completedSets; // 已完成组数
    private Integer completedReps; // 已完成次数
    private Boolean isCompleted = false; // 是否完成
    private String videoUrl; // 视频示范链接
    private String instructions; // 动作讲解
    private String notes;
    private Integer orderIndex;
}

