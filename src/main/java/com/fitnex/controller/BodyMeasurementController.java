package com.fitnex.controller;

import com.fitnex.entity.BodyMeasurement;
import com.fitnex.entity.User;
import com.fitnex.repository.BodyMeasurementRepository;
import com.fitnex.repository.UserRepository;
import com.fitnex.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/body-measurements")
@RequiredArgsConstructor
public class BodyMeasurementController {

    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<List<BodyMeasurement>> getBodyMeasurements(Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(bodyMeasurementRepository.findByUserIdOrderByMeasureDateDesc(userId));
    }

    @PostMapping
    public ResponseEntity<BodyMeasurement> createBodyMeasurement(
            @RequestBody BodyMeasurement measurement,
            Authentication authentication) {
        Long userId = securityUtil.getUserIdFromAuthentication(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        measurement.setUser(user);
        return ResponseEntity.ok(bodyMeasurementRepository.save(measurement));
    }
}

