package com.fitnex.service;

import com.fitnex.entity.User;
import com.fitnex.repository.UserRepository;
import com.fitnex.repository.WorkoutRecordRepository;
import com.fitnex.repository.NutritionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final NutritionRecordRepository nutritionRecordRepository;

    @Transactional
    public User updateProfile(Long userId, String username, String nickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (username != null && !username.isBlank()) {
            // 检查用户名是否已被其他用户使用
            Optional<User> existingUser = userRepository.findByUsername(username);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                throw new RuntimeException("用户名已被使用");
            }
            user.setUsername(username);
        }

        if (nickname != null) {
            user.setNickname(nickname);
        }

        return userRepository.save(user);
    }

    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            String avatarPath = fileService.saveImage(file);
            user.setAvatar(avatarPath);
            userRepository.save(user);

            return avatarPath;
        } catch (Exception e) {
            throw new RuntimeException("上传头像失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> getMemberInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 刷新会员等级
        refreshMemberLevel(user);

        // 计算当前积分和下一级所需积分
        LocalDate today = LocalDate.now();
        LocalDateTime last30Days = today.minusDays(30).atStartOfDay();
        int workoutCount = workoutRecordRepository
                .findByUserIdAndStartTimeBetween(user.getId(), last30Days, LocalDateTime.now())
                .size();
        int nutritionCount = nutritionRecordRepository
                .findByUserIdAndRecordDateBetween(user.getId(), today.minusDays(30), today)
                .size();
        double currentPoints = workoutCount * 2.0 + nutritionCount * 0.5
                + Optional.ofNullable(user.getTotalConsumption()).orElse(0.0) / 500.0;

        // 计算下一级所需积分
        double nextLevelPoints = getNextLevelPoints(user.getMemberLevel());

        Map<String, Object> memberInfo = new HashMap<>();
        memberInfo.put("currentLevel", user.getMemberLevel().name());
        memberInfo.put("currentPoints", (int) currentPoints);
        memberInfo.put("nextLevelPoints", (int) nextLevelPoints);
        memberInfo.put("progress", nextLevelPoints > 0 ? (currentPoints / nextLevelPoints) * 100 : 100);

        return memberInfo;
    }

    private void refreshMemberLevel(User user) {
        LocalDate today = LocalDate.now();
        LocalDateTime last30Days = today.minusDays(30).atStartOfDay();
        int workoutCount = workoutRecordRepository
                .findByUserIdAndStartTimeBetween(user.getId(), last30Days, LocalDateTime.now())
                .size();
        int nutritionCount = nutritionRecordRepository
                .findByUserIdAndRecordDateBetween(user.getId(), today.minusDays(30), today)
                .size();
        double compositeScore = workoutCount * 2.0 + nutritionCount * 0.5
                + Optional.ofNullable(user.getTotalConsumption()).orElse(0.0) / 500.0;

        User.MemberLevel level;
        if (compositeScore >= 50) {
            level = User.MemberLevel.DIAMOND;
        } else if (compositeScore >= 35) {
            level = User.MemberLevel.PLATINUM;
        } else if (compositeScore >= 20) {
            level = User.MemberLevel.GOLD;
        } else if (compositeScore >= 10) {
            level = User.MemberLevel.SILVER;
        } else {
            level = User.MemberLevel.BRONZE;
        }

        user.setMonthlyWorkoutCount(workoutCount);
        user.setMemberLevel(level);
        userRepository.save(user);
    }

    private double getNextLevelPoints(User.MemberLevel currentLevel) {
        switch (currentLevel) {
            case BRONZE:
                return 10;
            case SILVER:
                return 20;
            case GOLD:
                return 35;
            case PLATINUM:
                return 50;
            case DIAMOND:
                return 50; // 最高等级，不再升级
            default:
                return 10;
        }
    }
}
