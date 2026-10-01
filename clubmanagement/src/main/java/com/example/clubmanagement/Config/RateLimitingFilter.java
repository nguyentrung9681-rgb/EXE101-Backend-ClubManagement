package com.example.clubmanagement.Config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter xử lý Rate Limiting nhằm phòng chống DoS / DDoS ở tầng ứng dụng (Layer 7).
 * Sử dụng thuật toán Token Bucket (Bucket4j) để giới hạn số lượng request per IP.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.general-capacity:60}")
    private int generalCapacity;

    @Value("${app.security.rate-limit.auth-capacity:15}")
    private int authCapacity;

    // Lưu trữ bucket cho từng IP (Key: IP + ":" + Type)
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestURI = request.getRequestURI();
        
        // Bỏ qua kiểm tra Rate Limit cho tài nguyên tĩnh hoặc OpenAPI docs
        if (isExemptPath(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIP(request);
        boolean isAuthEndpoint = requestURI.startsWith("/api/auth/") || requestURI.startsWith("/login");

        String bucketKey = clientIp + (isAuthEndpoint ? ":AUTH" : ":GENERAL");
        int capacity = isAuthEndpoint ? authCapacity : generalCapacity;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createNewBucket(capacity));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {} on URI: {}", clientIp, requestURI);
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", "60");

            String jsonResponse = String.format(
                    "{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"Hệ thống ghi nhận quá nhiều yêu cầu từ địa chỉ IP của bạn. Vui lòng thử lại sau ít phút.\", \"path\": \"%s\"}",
                    requestURI
            );
            
            response.getWriter().write(jsonResponse);
        }
    }

    private Bucket createNewBucket(int capacity) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isExemptPath(String path) {
        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".png") ||
               path.endsWith(".jpg") ||
               path.endsWith(".ico");
    }

    /**
     * Lấy IP thực tế của Client kể cả khi đứng sau Proxy / CDN (Cloudflare, Nginx, Vercel, Render)
     */
    private String getClientIP(HttpServletRequest request) {
        // Cloudflare IP Header
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.trim().isEmpty() && !"unknown".equalsIgnoreCase(cfIp)) {
            return cfIp.trim();
        }

        // X-Real-IP
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }

        // Standard X-Forwarded-For
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.trim().isEmpty() && !"unknown".equalsIgnoreCase(xfHeader)) {
            return xfHeader.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
