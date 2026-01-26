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
@Table(name = "health_analyses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class HealthAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    private LocalDate analysisDate;
    private Double progressScore; // 进度评分 0-100
    private String progressLevel; // 进度等级：优秀、良好、一般、需改进
    private Integer totalWorkouts; // 总训练次数
    private Integer totalCaloriesBurned; // 总消耗卡路里
    private Double weightChange; // 体重变化
    private Double bodyFatChange; // 体脂率变化
    private String riskWarnings; // 风险预警（JSON格式）
    private String recommendations; // 建议（JSON格式）
    private String analysisData; // 分析数据（JSON格式，包含图表数据）

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

