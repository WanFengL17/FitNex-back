package com.fitnex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column(nullable = false)
    private String password;

    private String nickname;
    private String avatar;
    /**
     * 第三方账号唯一标识
     */
    private String thirdPartyId;
    /**
     * 第三方平台名称（如 WECHAT, GOOGLE）
     */
    private String thirdPartyProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberLevel memberLevel = MemberLevel.BRONZE;

    @Enumerated(EnumType.STRING)
    private LoginType loginType = LoginType.EMAIL;

    private Boolean enabled = true;
    /**
     * 近30天训练次数缓存，用于会员等级计算
     */
    private Integer monthlyWorkoutCount = 0;
    /**
     * 消费金额（单位：元），用于会员等级计算
     */
    private Double totalConsumption = 0.0;
    /**
     * 最近一次登录时间
     */
    private LocalDateTime lastLoginAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum UserRole {
        USER, ADMIN
    }

    public enum MemberLevel {
        BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
    }

    public enum LoginType {
        EMAIL, PHONE, WECHAT, QQ
    }
}

