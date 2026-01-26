package com.fitnex.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "workout_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class WorkoutRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    @JsonIgnore
    private WorkoutPlan workoutPlan;

    private String workoutName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration; // 总时长（秒）
    private Integer caloriesBurned; // 消耗卡路里
    private Double averageHeartRate; // 平均心率
    private Double maxHeartRate; // 最大心率
    private Double minHeartRate; // 最小心率
    private String status; // 状态：IN_PROGRESS, COMPLETED, PAUSED, CANCELLED
    private Boolean isShared = false; // 是否已分享
    private String shareCode; // 分享码
    private String notes; // 备注

    @OneToMany(mappedBy = "workoutRecord", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<WorkoutExerciseRecord> exerciseRecords;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

