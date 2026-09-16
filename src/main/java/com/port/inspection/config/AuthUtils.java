package com.port.inspection.config;

import com.port.inspection.exception.BizException;
import com.port.inspection.model.User;
import org.springframework.security.core.context.SecurityContextHolder;

/** 获取当前登录用户 */
public final class AuthUtils {

    private AuthUtils() {}

    public static User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new BizException("未登录或登录已过期", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}
