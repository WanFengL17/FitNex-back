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
import java.util.List;

@Entity
@Table(name = "nutrition_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class NutritionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    private LocalDate recordDate;
    private String mealType; // BREAKFAST, LUNCH, DINNER, SNACK
    private String foodName;
    private Double quantity; // 数量
    private String unit; // 单位：克、毫升、份等
    private Integer calories;
    private Double protein; // 蛋白质（克）
    private Double carbs; // 碳水化合物（克）
    private Double fat; // 脂肪（克）
    private Double fiber; // 纤维（克）
    private String imageUrl; // 食物图片（用于图像识别）
    private Boolean isAiRecognized = false; // 是否AI识别
    private String notes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

