package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.GoogleAccount;
import com.example.clubmanagement.Entity.GoogleForm;
import com.example.clubmanagement.Entity.SheetFormType;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Repository.GoogleAccountRepository;
import com.example.clubmanagement.Repository.GoogleFormRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.GoogleFormQuestionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
public class GoogleFormsService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    private final GoogleAccountRepository googleAccountRepository;
    private final GoogleFormRepository googleFormRepository;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleFormsService(GoogleAccountRepository googleAccountRepository,
                              GoogleFormRepository googleFormRepository,
                              GoogleCalendarService googleCalendarService,
                              UserRepository userRepository) {
        this.googleAccountRepository = googleAccountRepository;
        this.googleFormRepository = googleFormRepository;
        this.googleCalendarService = googleCalendarService;
        this.userRepository = userRepository;
    }

    /**
     * Lấy URL để kết nối tài khoản Google với đầy đủ Scope cho Forms và Drive.
     */
    public String getAuthorizeUrl(Integer userId) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/forms.body https://www.googleapis.com/auth/forms.responses.readonly https://www.googleapis.com/auth/drive.file")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", userId.toString())
                .build().toUriString();
    }

    private GoogleAccount getActiveGoogleAccount(Integer userId) throws Exception {
        GoogleAccount account = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new RuntimeException("Tài khoản Google chưa được kết nối! Vui lòng liên kết tài khoản trước."));
        return googleCalendarService.refreshAccessToken(account);
    }

    /**
     * Lấy danh sách các biểu mẫu trong database của người dùng.
     */
    public List<GoogleForm> getFormsByUser(Integer userId) {
        return googleFormRepository.findByUserUserId(userId);
    }

    /**
     * Tạo một Google Form mới và lưu thông tin vào database.
     */
    @Transactional
    public GoogleForm createForm(Integer userId, String title, SheetFormType type) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("Loại (type) là bắt buộc! Vui lòng chọn EVENT hoặc CLUB_ACTIVITIES.");
        }
        GoogleAccount activeAccount = getActiveGoogleAccount(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng trong hệ thống!"));

        // Gọi Google Forms API để tạo file form
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> info = new HashMap<>();
        info.put("title", title);

        Map<String, Object> body = new HashMap<>();
        body.put("info", info);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://forms.googleapis.com/v1/forms",
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Tạo Google Form thất bại: " + response.getBody());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String formId = root.get("formId").asText();
        String responderUri = root.get("responderUri").asText();
        String formUrl = "https://docs.google.com/forms/d/" + formId + "/edit";

        GoogleForm googleForm = GoogleForm.builder()
                .formId(formId)
                .title(title)
                .type(type)
                .formUrl(formUrl)
                .responderUri(responderUri)
                .user(user)
                .build();

        return googleFormRepository.save(googleForm);
    }

    /**
     * Đọc cấu trúc chi tiết của một Google Form.
     */
    public Map<String, Object> getFormDetails(Integer userId, String formId) throws Exception {
        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://forms.googleapis.com/v1/forms/" + formId;
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lấy chi tiết Google Form thất bại: " + response.getBody());
        }

        return objectMapper.readValue(response.getBody(), Map.class);
    }

    /**
     * Lấy các phản hồi (responses) của Google Form.
     */
    public Map<String, Object> getFormResponses(Integer userId, String formId) throws Exception {
        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://forms.googleapis.com/v1/forms/" + formId + "/responses";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lấy danh sách phản hồi Google Form thất bại: " + response.getBody());
        }

        return objectMapper.readValue(response.getBody(), Map.class);
    }

    /**
     * Thêm câu hỏi vào Google Form sử dụng batchUpdate.
     */
    @Transactional
    public String addQuestion(Integer userId, String formId, GoogleFormQuestionRequest questionRequest) throws Exception {
        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        // Xây dựng request body cho batchUpdate
        Map<String, Object> createItem = new HashMap<>();
        Map<String, Object> item = new HashMap<>();
        item.put("title", questionRequest.getTitle());

        Map<String, Object> questionItem = new HashMap<>();
        Map<String, Object> question = new HashMap<>();
        question.put("required", questionRequest.getRequired() != null && questionRequest.getRequired());

        String type = questionRequest.getType() != null ? questionRequest.getType().toUpperCase() : "TEXT";
        if (type.equals("MULTIPLE_CHOICE") || type.equals("CHECKBOX")) {
            Map<String, Object> choiceQuestion = new HashMap<>();
            choiceQuestion.put("type", type.equals("MULTIPLE_CHOICE") ? "RADIO" : "CHECKBOX");

            List<Map<String, Object>> optionsList = new ArrayList<>();
            if (questionRequest.getOptions() != null) {
                for (String optVal : questionRequest.getOptions()) {
                    Map<String, Object> option = new HashMap<>();
                    option.put("value", optVal);
                    optionsList.add(option);
                }
            }
            choiceQuestion.put("options", optionsList);
            question.put("choiceQuestion", choiceQuestion);
        } else {
            // TEXT hoặc PARAGRAPH
            Map<String, Object> textQuestion = new HashMap<>();
            textQuestion.put("paragraph", type.equals("PARAGRAPH"));
            question.put("textQuestion", textQuestion);
        }

        questionItem.put("question", question);
        item.put("questionItem", questionItem);

        createItem.put("item", item);
        // Chèn vào vị trí đầu tiên
        Map<String, Object> location = new HashMap<>();
        location.put("index", 0);
        createItem.put("location", location);

        Map<String, Object> requestItem = new HashMap<>();
        requestItem.put("createItem", createItem);

        Map<String, Object> body = new HashMap<>();
        body.put("requests", List.of(requestItem));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://forms.googleapis.com/v1/forms/" + formId + ":batchUpdate";

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Thêm câu hỏi thất bại: " + response.getBody());
        }

        return response.getBody();
    }

    /**
     * Xóa Google Form khỏi database và xóa file trên Drive của người dùng.
     */
    @Transactional
    public void deleteForm(Integer userId, String formId) throws Exception {
        GoogleForm googleForm = googleFormRepository.findByFormIdAndUserUserId(formId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy biểu mẫu trong hệ thống hoặc bạn không có quyền xóa!"));

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        // Gọi Google Drive API để xóa file biểu mẫu
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://www.googleapis.com/drive/v3/files/" + formId;
        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode() != HttpStatus.NOT_FOUND) {
                System.err.println("Xóa file trên Google Drive thất bại: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi kết nối Google Drive API để xóa form: " + e.getMessage());
        }

        googleFormRepository.delete(googleForm);
    }
}
