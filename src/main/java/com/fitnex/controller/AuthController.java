package com.fitnex.controller;

import com.fitnex.dto.AuthRequest;
import com.fitnex.dto.AuthResponse;
import com.fitnex.dto.RegisterRequest;
import com.fitnex.entity.User;
import com.fitnex.repository.UserRepository;
import com.fitnex.service.AuthService;
import com.fitnex.util.JwtTokenUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            if (userRepository.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest().body("用户名已存在");
            }
            if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest().body("邮箱已被注册");
            }
            if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
                return ResponseEntity.badRequest().body("手机号已被注册");
            }

            User user = new User();
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
            user.setLoginType(request.getLoginType());
            user.setThirdPartyId(request.getThirdPartyId());
            user.setThirdPartyProvider(request.getThirdPartyProvider());

            User savedUser = userRepository.save(user);
            String token = jwtTokenUtil.generateToken(
                org.springframework.security.core.userdetails.User.builder()
                    .username(savedUser.getUsername())
                    .password(savedUser.getPassword())
                    .authorities(new java.util.ArrayList<>())
                    .build()
            );

            return ResponseEntity.ok(authService.buildAuthResponse(savedUser, token));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("注册失败: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            return authService.authenticate(request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("登录失败: " + e.getMessage());
        }
    }

    @PostMapping("/third-party")
    public ResponseEntity<?> thirdPartyLogin(@Valid @RequestBody com.fitnex.dto.ThirdPartyAuthRequest request) {
        try {
            return authService.authenticateThirdParty(request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("第三方登录失败: " + e.getMessage());
        }
    }
}

