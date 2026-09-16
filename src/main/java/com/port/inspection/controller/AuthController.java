package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody Dtos.LoginRequest req) {
        return authService.login(req.username(), req.password());
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return authService.publicView(AuthUtils.currentUser());
    }
}
