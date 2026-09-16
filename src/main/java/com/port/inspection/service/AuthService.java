package com.port.inspection.service;

import com.port.inspection.config.JwtService;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.User;
import com.port.inspection.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        String token = jwtService.generate(user.getUsername(), user.getRole().name(), user.getId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("user", publicView(user));
        return resp;
    }

    public Map<String, Object> publicView(User user) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", user.getId());
        v.put("username", user.getUsername());
        v.put("displayName", user.getDisplayName());
        v.put("role", user.getRole().name());
        v.put("merchantId", user.getMerchantId());
        return v;
    }
}
