package com.example.clubmanagement.Config;

import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;

    private static final Pattern DEPT_TOPIC_PATTERN = Pattern.compile("^/topic/clubs/(\\d+)/departments/(\\d+)$");
    private static final Pattern CLUB_TOPIC_PATTERN = Pattern.compile("^/topic/clubs/(\\d+)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // 1. Xác thực khi CONNECT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromJWT(token);
                User user = userRepository.findByUsername(username)
                        .or(() -> userRepository.findByEmail(username))
                        .orElseThrow(() -> new AccessDeniedException("User không tồn tại"));

                if ("BANNED".equals(user.getUserStatus()) || "INACTIVE".equals(user.getUserStatus())) {
                    log.warn("STOMP kết nối thất bại: Tài khoản user={} bị khóa hoặc chưa kích hoạt", username);
                    throw new AccessDeniedException("Tài khoản đã bị khóa hoặc chưa kích hoạt");
                }

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

                accessor.setUser(auth);
                SecurityContextHolder.getContext().setAuthentication(auth);

                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null) {
                    sessionAttributes.put("userId", user.getUserId());
                    sessionAttributes.put("username", user.getUsername());
                    sessionAttributes.put("email", user.getEmail());
                }
                log.info("STOMP kết nối thành công: userId={}, username={}", user.getUserId(), user.getUsername());
            } else {
                log.warn("STOMP kết nối thất bại: Token không hợp lệ hoặc thiếu");
                throw new AccessDeniedException("JWT token không hợp lệ hoặc thiếu");
            }
        }

        // 2. Kiểm tra quyền khi SUBSCRIBE vào Topic
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            Integer userId = sessionAttributes != null ? (Integer) sessionAttributes.get("userId") : null;

            if (destination != null && userId != null) {
                validateSubscriptionPermission(destination, userId);
            }
        }

        return message;
    }

    private void validateSubscriptionPermission(String destination, Integer userId) {
        // Kiểm tra quyền phòng ban: /topic/clubs/{clubId}/departments/{departmentId}
        Matcher deptMatcher = DEPT_TOPIC_PATTERN.matcher(destination);
        if (deptMatcher.matches()) {
            Integer clubId = Integer.parseInt(deptMatcher.group(1));
            Integer deptId = Integer.parseInt(deptMatcher.group(2));

            ClubMember member = clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                    .orElseThrow(() -> new AccessDeniedException("Bạn không phải thành viên hoạt động của CLB này"));

            if (member.getDepartment() == null || !member.getDepartment().getId().equals(deptId)) {
                throw new AccessDeniedException("Bạn không thuộc phòng ban này để tham gia kênh chat");
            }
            return;
        }

        // Kiểm tra quyền CLB chung: /topic/clubs/{clubId}
        Matcher clubMatcher = CLUB_TOPIC_PATTERN.matcher(destination);
        if (clubMatcher.matches()) {
            Integer clubId = Integer.parseInt(clubMatcher.group(1));
            clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                    .orElseThrow(() -> new AccessDeniedException("Bạn không phải thành viên hoạt động của CLB này"));
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // Thử lấy từ header "Authorization"
        String bearerToken = accessor.getFirstNativeHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // Thử lấy từ header "token"
        String rawToken = accessor.getFirstNativeHeader("token");
        if (rawToken != null && !rawToken.isBlank()) {
            return rawToken;
        }
        // Thử lấy từ query parameter nếu có truyền qua url
        return null;
    }
}