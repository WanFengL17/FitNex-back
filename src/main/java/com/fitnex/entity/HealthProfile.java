package com.fitnex.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class HealthProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    private LocalDate birthDate;
    private String gender; // MALE, FEMALE, OTHER
    private Double height; // 厘米
    private Double weight; // 公斤
    private Double bmi;
    private Double bodyFat; // 体脂率
    private Double muscleMass; // 肌肉量
    private String bloodType;
    private String medicalHistory; // 病史
    private String fitnessGoal; // 健身目标：减脂、增肌、塑形、健康等
    private String activityLevel; // 活动水平：久坐、轻度、中度、高度、极高
    private Integer targetWeight; // 目标体重
    private Integer targetCalories; // 目标卡路里
    private String allergies; // 过敏信息
    private String dietaryRestrictions; // 饮食限制

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

