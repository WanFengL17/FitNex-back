package com.fitnex.controller;

import com.fitnex.entity.HealthProfile;
import com.fitnex.service.HealthProfileService;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/health-profile")
@RequiredArgsConstructor
public class HealthProfileController {

    private final HealthProfileService healthProfileService;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<HealthProfile> getHealthProfile(Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        HealthProfile profile = healthProfileService.getHealthProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PostMapping
    public ResponseEntity<HealthProfile> createOrUpdateHealthProfile(
            @RequestBody HealthProfile profile,
            Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        HealthProfile savedProfile = healthProfileService.createOrUpdateHealthProfile(userId, profile);
        return ResponseEntity.ok(savedProfile);
    }
}

