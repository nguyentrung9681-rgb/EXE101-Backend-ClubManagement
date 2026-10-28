package com.example.clubmanagement.Config;

import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.SystemRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityUtils {

    public static Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static Integer getCurrentUserId() {
        return getCurrentUser().map(User::getUserId).orElse(null);
    }

    public static void validateUserPermission(Integer requestedUserId) {
        User currentUser = getCurrentUser().orElse(null);
        if (currentUser == null) {
            throw new SecurityException("Yêu cầu không được xác thực! Vui lòng cung cấp JWT Token hợp lệ.");
        }
        if (requestedUserId != null && !currentUser.getUserId().equals(requestedUserId) && currentUser.getRole() != SystemRole.ADMIN) {
            throw new SecurityException("Bạn không có quyền thực hiện thao tác trên tài khoản của người dùng khác!");
        }
    }

    public static void validateAdminPermission() {
        User currentUser = getCurrentUser().orElse(null);
        if (currentUser == null) {
            throw new SecurityException("Yêu cầu không được xác thực! Vui lòng cung cấp JWT Token hợp lệ.");
        }
        if (currentUser.getRole() != SystemRole.ADMIN) {
            throw new SecurityException("Bạn không có quyền thực hiện thao tác quản trị này!");
        }
    }

    public static Integer resolveUserId(Integer requestedUserId) {
        if (requestedUserId != null) {
            validateUserPermission(requestedUserId);
            return requestedUserId;
        }
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("Yêu cầu không được xác thực! Vui lòng cung cấp JWT Token hợp lệ.");
        }
        return currentUserId;
    }
}
