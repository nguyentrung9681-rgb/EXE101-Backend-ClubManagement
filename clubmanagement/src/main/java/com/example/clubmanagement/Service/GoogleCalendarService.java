package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubEvent;
import com.example.clubmanagement.Entity.EventGoogleSync;
import com.example.clubmanagement.Entity.GoogleAccount;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Repository.GoogleAccountRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class GoogleCalendarService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    private final GoogleAccountRepository googleAccountRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleCalendarService(GoogleAccountRepository googleAccountRepository, UserRepository userRepository) {
        this.googleAccountRepository = googleAccountRepository;
        this.userRepository = userRepository;
    }

    /**
     * Tạo URL OAuth2 để xin quyền kết nối Google Calendar (offline access để lấy refresh token).
     */
    public String getAuthorizeUrl(Integer userId) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", codeChallengeOrResponse())
                .queryParam("scope", "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/userinfo.email")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", userId.toString())
                .build().toUriString();
    }

    private String codeChallengeOrResponse() {
        return "code";
    }

    /**
     * Nhận authorization code, đổi lấy Access Token và Refresh Token, sau đó lưu vào DB.
     */
    public GoogleAccount exchangeCodeForTokens(Integer userId, String code) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("https://oauth2.googleapis.com/token", request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Đổi token thất bại: " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        String accessToken = jsonNode.get("access_token").asText();
        String refreshToken = jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null;
        int expiresIn = jsonNode.get("expires_in").asInt();
        String scope = jsonNode.has("scope") ? jsonNode.get("scope").asText() : null;

        // Lấy thông tin email từ Google UserInfo
        String email = fetchGoogleEmail(accessToken);

        // Tìm tài khoản google cũ để cập nhật hoặc tạo mới
        GoogleAccount account = googleAccountRepository.findByUserUserIdAndGoogleEmail(userId, email)
                .orElse(GoogleAccount.builder().user(user).googleEmail(email).build());

        account.setAccessToken(accessToken);
        if (refreshToken != null) {
            account.setRefreshToken(refreshToken);
        }
        account.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
        account.setScope(scope);

        return googleAccountRepository.save(account);
    }

    /**
     * Tự động làm mới Access Token nếu đã hết hạn.
     */
    public GoogleAccount refreshAccessToken(GoogleAccount account) throws Exception {
        if (!account.isExpired()) {
            return account;
        }

        if (account.getRefreshToken() == null) {
            throw new RuntimeException("Không có Refresh Token để làm mới Access Token!");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", account.getRefreshToken());
        body.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("https://oauth2.googleapis.com/token", request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Làm mới access token thất bại: " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        String newAccessToken = jsonNode.get("access_token").asText();
        int expiresIn = jsonNode.get("expires_in").asInt();

        account.setAccessToken(newAccessToken);
        account.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
        return googleAccountRepository.save(account);
    }

    /**
     * Lấy email tài khoản Google bằng access token.
     */
    private String fetchGoogleEmail(String accessToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lấy thông tin email Google thất bại!");
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.get("email").asText();
    }

    /**
     * Đồng bộ: Tạo sự kiện mới trên Google Calendar.
     */
    public Map<String, String> createEvent(ClubEvent event, GoogleAccount account) throws Exception {
        GoogleAccount activeAccount = refreshAccessToken(account);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> body = new HashMap<>();
        body.put("summary", event.getTitle());
        body.put("description", event.getDescription());
        body.put("location", event.getLocation());

        Map<String, String> start = new HashMap<>();
        start.put("dateTime", formatToIsoOffsetDateTime(event.getStartTime()));
        body.put("start", start);

        Map<String, String> end = new HashMap<>();
        end.put("dateTime", formatToIsoOffsetDateTime(event.getEndTime()));
        body.put("end", end);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://www.googleapis.com/calendar/v3/calendars/primary/events",
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Tạo event trên Google thất bại: " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        Map<String, String> result = new HashMap<>();
        result.put("googleEventId", jsonNode.get("id").asText());
        result.put("googleEventLink", jsonNode.has("htmlLink") ? jsonNode.get("htmlLink").asText() : "");
        result.put("googleEtag", jsonNode.has("etag") ? jsonNode.get("etag").asText().replace("\"", "") : "");

        return result;
    }

    /**
     * Đồng bộ: Cập nhật sự kiện trên Google Calendar.
     */
    public Map<String, String> updateEvent(ClubEvent event, EventGoogleSync sync, GoogleAccount account) throws Exception {
        GoogleAccount activeAccount = refreshAccessToken(account);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> body = new HashMap<>();
        body.put("summary", event.getTitle());
        body.put("description", event.getDescription());
        body.put("location", event.getLocation());

        Map<String, String> start = new HashMap<>();
        start.put("dateTime", formatToIsoOffsetDateTime(event.getStartTime()));
        body.put("start", start);

        Map<String, String> end = new HashMap<>();
        end.put("dateTime", formatToIsoOffsetDateTime(event.getEndTime()));
        body.put("end", end);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = String.format("https://www.googleapis.com/calendar/v3/calendars/%s/events/%s",
                sync.getGoogleCalendarId() != null ? sync.getGoogleCalendarId() : "primary",
                sync.getGoogleEventId());

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Cập nhật event trên Google thất bại: " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        Map<String, String> result = new HashMap<>();
        result.put("googleEtag", jsonNode.has("etag") ? jsonNode.get("etag").asText().replace("\"", "") : "");
        return result;
    }

    /**
     * Đồng bộ: Xóa sự kiện trên Google Calendar.
     */
    public void deleteEvent(EventGoogleSync sync, GoogleAccount account) throws Exception {
        GoogleAccount activeAccount = refreshAccessToken(account);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = String.format("https://www.googleapis.com/calendar/v3/calendars/%s/events/%s",
                sync.getGoogleCalendarId() != null ? sync.getGoogleCalendarId() : "primary",
                sync.getGoogleEventId());

        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                Void.class
        );

        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode() != HttpStatus.NOT_FOUND) {
            throw new RuntimeException("Xóa event trên Google thất bại");
        }
    }

    private String formatToIsoOffsetDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return localDateTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
