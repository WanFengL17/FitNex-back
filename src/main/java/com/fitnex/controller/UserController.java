package com.fitnex.controller;

import com.fitnex.entity.User;
import com.fitnex.service.UserService;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SecurityUtil securityUtil;

    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        String username = request.get("username");
        String nickname = request.get("nickname");
        User updatedUser = userService.updateProfile(userId, username, nickname);
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping("/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        String avatarPath = userService.uploadAvatar(userId, file);
        return ResponseEntity.ok(Map.of("avatar", avatarPath, "url", avatarPath));
    }

    @GetMapping("/member-info")
    public ResponseEntity<Map<String, Object>> getMemberInfo(Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        Map<String, Object> memberInfo = userService.getMemberInfo(userId);
        return ResponseEntity.ok(memberInfo);
    }
}
