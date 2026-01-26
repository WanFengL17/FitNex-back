package com.fitnex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlanSummaryDto {
    private Long id;
    private String name;
    private String goal;
    private Integer duration;
    private Integer frequency;
    private String difficulty;
    private Boolean isActive;
    private Boolean isAiGenerated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer totalWorkouts;
    private Integer totalCalories;
    private Double adherenceRate;
    private String progressTrend;
}








