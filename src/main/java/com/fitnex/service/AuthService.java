package com.fitnex.service;

import com.fitnex.dto.AuthRequest;
import com.fitnex.dto.AuthResponse;
import com.fitnex.dto.ThirdPartyAuthRequest;
import com.fitnex.entity.User;
import com.fitnex.entity.User.MemberLevel;
import com.fitnex.repository.UserRepository;
import com.fitnex.repository.NutritionRecordRepository;
import com.fitnex.repository.WorkoutRecordRepository;
import com.fitnex.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final NutritionRecordRepository nutritionRecordRepository;
    private final UserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;

    public ResponseEntity<?> authenticate(AuthRequest request) {
        try {
            String identifier = Optional.ofNullable(request.getIdentifier())
                    .orElseThrow(() -> new RuntimeException("登录标识不能为空"));

            User user = findUserByIdentifier(identifier)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            if (request.getLoginType() == User.LoginType.WECHAT || request.getLoginType() == User.LoginType.QQ) {
                // 第三方账号无需密码校验
                user.setLoginType(request.getLoginType());
            } else {
                if (request.getPassword() == null || request.getPassword().isBlank()) {
                    throw new RuntimeException("密码不能为空");
                }
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                user.getUsername(),
                                request.getPassword()
                        )
                );
            }

            user.setLastLoginAt(LocalDateTime.now());
            refreshMemberLevel(user);
            userRepository.save(user);

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtTokenUtil.generateToken(userDetails);

            return ResponseEntity.ok(buildAuthResponse(user, token));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("用户名或密码错误");
        }
    }

    public ResponseEntity<?> authenticateThirdParty(ThirdPartyAuthRequest request) {
        User.LoginType loginType = request.getLoginType() != null ? request.getLoginType() : User.LoginType.WECHAT;
        String provider = Optional.ofNullable(request.getThirdPartyProvider()).orElse(loginType.name());

        User user = userRepository.findByThirdPartyProviderAndThirdPartyId(provider, request.getThirdPartyId())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setUsername(provider.toLowerCase() + "_" + request.getThirdPartyId());
                    newUser.setThirdPartyId(request.getThirdPartyId());
                    newUser.setThirdPartyProvider(provider);
                    newUser.setLoginType(loginType);
                    newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    newUser.setEmail(request.getEmail());
                    newUser.setPhone(request.getPhone());
                    newUser.setNickname(Optional.ofNullable(request.getNickname()).orElse("第三方用户"));
                    return userRepository.save(newUser);
                });

        user.setLastLoginAt(LocalDateTime.now());
        refreshMemberLevel(user);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtTokenUtil.generateToken(userDetails);
        return ResponseEntity.ok(buildAuthResponse(user, token));
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .or(() -> userRepository.findByPhone(identifier));
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

        MemberLevel level;
        if (compositeScore >= 50) {
            level = MemberLevel.DIAMOND;
        } else if (compositeScore >= 35) {
            level = MemberLevel.PLATINUM;
        } else if (compositeScore >= 20) {
            level = MemberLevel.GOLD;
        } else if (compositeScore >= 10) {
            level = MemberLevel.SILVER;
        } else {
            level = MemberLevel.BRONZE;
        }

        user.setMonthlyWorkoutCount(workoutCount);
        user.setMemberLevel(level);
    }

    public AuthResponse buildAuthResponse(User user, String token) {
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());
        response.setMemberLevel(user.getMemberLevel().name());
        return response;
    }
}

