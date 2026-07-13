package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.GoogleAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleDocumentService {

    @Value("${google.webhook-url}")
    private String webhookBaseUrl;

    private final GoogleCalendarService googleCalendarService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleDocumentService(GoogleCalendarService googleCalendarService) {
        this.googleCalendarService = googleCalendarService;
    }

    /**
     * Tạo tài liệu mới trên Google Docs.
     */
    public Map<String, String> createDocument(String title, GoogleAccount account) throws Exception {
        GoogleAccount activeAccount = googleCalendarService.refreshAccessToken(account);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> body = new HashMap<>();
        body.put("title", title);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://docs.googleapis.com/v1/documents",
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Tạo Google Doc thất bại: " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        String docId = jsonNode.get("documentId").asText();
        String docUrl = "https://docs.google.com/document/d/" + docId + "/edit";

        return Map.of("documentId", docId, "documentUrl", docUrl);
    }

    /**
     * Chia sẻ tài liệu cho bất kỳ ai có link với quyền tùy chọn (writer, commenter, reader).
     */
    public void shareDocument(String documentId, String role, GoogleAccount account) throws Exception {
        GoogleAccount activeAccount = googleCalendarService.refreshAccessToken(account);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> body = new HashMap<>();
        body.put("role", role);
        body.put("type", "anyone");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://www.googleapis.com/drive/v3/files/" + documentId + "/permissions";

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Chia sẻ Google Doc thất bại: " + response.getBody());
        }
    }

    /**
     * Đăng ký Webhook Watch để Google gửi push notification khi file thay đổi.
     */
    public Map<String, Object> watchDocumentChanges(String documentId, GoogleAccount account) throws Exception {
        GoogleAccount activeAccount = googleCalendarService.refreshAccessToken(account);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        String channelId = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("id", channelId);
        body.put("type", "web_hook");
        body.put("address", webhookBaseUrl + "/api/documents/webhook");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://www.googleapis.com/drive/v3/files/" + documentId + "/watch";

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Tạo Watch Webhook thất bại: " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        Map<String, Object> result = new HashMap<>();
        result.put("channelId", channelId);
        result.put("resourceId", jsonNode.get("resourceId").asText());
        result.put("expiration", jsonNode.get("expiration").asLong()); // Epoch ms
        return result;
    }

    /**
     * Tải nội dung tài liệu thô dưới dạng Plain Text.
     */
    public String fetchDocumentText(String documentId, GoogleAccount account) throws Exception {
        GoogleAccount activeAccount = googleCalendarService.refreshAccessToken(account);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = "https://www.googleapis.com/drive/v3/files/" + documentId + "/export?mimeType=text/plain";

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Tải nội dung text Google Doc thất bại");
        }

        byte[] bytes = response.getBody();
        if (bytes == null) {
            return "";
        }

        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (text.startsWith("\ufeff")) {
            text = text.substring(1);
        }
        return text;
    }
}
