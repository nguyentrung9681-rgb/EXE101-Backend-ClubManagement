package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.GoogleAccount;
import com.example.clubmanagement.Entity.GoogleForm;
import com.example.clubmanagement.Entity.SheetFormType;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Entity.GoogleSheet;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.GoogleAccountRepository;
import com.example.clubmanagement.Repository.GoogleFormRepository;
import com.example.clubmanagement.Repository.GoogleSheetRepository;
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
    private final GoogleSheetRepository googleSheetRepository;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final ClubPermissionService clubPermissionService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleFormsService(GoogleAccountRepository googleAccountRepository,
                              GoogleFormRepository googleFormRepository,
                              GoogleSheetRepository googleSheetRepository,
                              GoogleCalendarService googleCalendarService,
                              UserRepository userRepository,
                              ClubRepository clubRepository,
                              ClubPermissionService clubPermissionService) {
        this.googleAccountRepository = googleAccountRepository;
        this.googleFormRepository = googleFormRepository;
        this.googleSheetRepository = googleSheetRepository;
        this.googleCalendarService = googleCalendarService;
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.clubPermissionService = clubPermissionService;
    }

    /**
     * Lấy URL để kết nối tài khoản Google với đầy đủ Scope cho Forms và Drive.
     */
    public String getAuthorizeUrl(Integer userId) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "https://www.googleapis.com/auth/userinfo.email " +
                        "https://www.googleapis.com/auth/forms.body " +
                        "https://www.googleapis.com/auth/forms.responses.readonly " +
                        "https://www.googleapis.com/auth/spreadsheets " +
                        "https://www.googleapis.com/auth/drive.file")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", userId.toString())
                .build().toUriString();
    }

    /**
     * Lấy tài khoản Google hợp lệ (tự động làm mới access token nếu cần).
     */
    private GoogleAccount getActiveGoogleAccount(Integer userId) throws Exception {
        GoogleAccount account = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new RuntimeException(
                        "Tài khoản Google chưa được kết nối! Vui lòng liên kết tài khoản trước."));
        return googleCalendarService.refreshAccessToken(account);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE — Chỉ PRESIDENT / TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo một Google Form mới trong môi trường CLB chỉ định.
     * Yêu cầu: người dùng là PRESIDENT hoặc TREASURER của CLB đó.
     *
     * @param userId  ID người dùng thực hiện thao tác
     * @param clubId  ID của CLB mà Form được tạo trong đó
     * @param title   Tiêu đề Google Form
     * @param type    Loại form: EVENT hoặc CLUB_ACTIVITIES
     */
    @Transactional
    public GoogleForm createForm(Integer userId, Integer clubId, String title, SheetFormType type) throws Exception {
        // ── Kiểm tra phân quyền ──
        clubPermissionService.requireCanCreate(userId, clubId);

        if (type == null) {
            throw new IllegalArgumentException("Loại (type) là bắt buộc! Vui lòng chọn EVENT hoặc CLUB_ACTIVITIES.");
        }

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng trong hệ thống!"));
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy CLB trong hệ thống!"));

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

        // Tự động tạo file Google Sheet tương ứng cho Form để lưu phản hồi
        String linkedSpreadsheetId = null;
        String linkedSpreadsheetUrl = null;
        try {
            String sheetTitle = title + " (Phản hồi)";
            Map<String, Object> sheetProperties = new HashMap<>();
            sheetProperties.put("title", sheetTitle);

            Map<String, Object> sheetBody = new HashMap<>();
            sheetBody.put("properties", sheetProperties);

            HttpEntity<Map<String, Object>> sheetRequest = new HttpEntity<>(sheetBody, headers);
            ResponseEntity<String> sheetResponse = restTemplate.postForEntity(
                    "https://sheets.googleapis.com/v4/spreadsheets",
                    sheetRequest,
                    String.class
            );

            if (sheetResponse.getStatusCode().is2xxSuccessful()) {
                JsonNode sheetRoot = objectMapper.readTree(sheetResponse.getBody());
                linkedSpreadsheetId = sheetRoot.get("spreadsheetId").asText();
                linkedSpreadsheetUrl = sheetRoot.has("spreadsheetUrl")
                        ? sheetRoot.get("spreadsheetUrl").asText()
                        : "https://docs.google.com/spreadsheets/d/" + linkedSpreadsheetId + "/edit";

                GoogleSheet googleSheet = GoogleSheet.builder()
                        .spreadsheetId(linkedSpreadsheetId)
                        .title(sheetTitle)
                        .type(type)
                        .spreadsheetUrl(linkedSpreadsheetUrl)
                        .user(user)
                        .club(club)
                        .build();

                googleSheetRepository.save(googleSheet);
            }
        } catch (Exception e) {
            System.err.println("Tự động tạo Google Sheet liên kết thất bại: " + e.getMessage());
        }

        GoogleForm googleForm = GoogleForm.builder()
                .formId(formId)
                .title(title)
                .type(type)
                .formUrl(formUrl)
                .responderUri(responderUri)
                .linkedSpreadsheetId(linkedSpreadsheetId)
                .linkedSpreadsheetUrl(linkedSpreadsheetUrl)
                .user(user)
                .club(club)
                .build();

        return googleFormRepository.save(googleForm);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ — Mọi thành viên ACTIVE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách Google Form thuộc CLB chỉ định.
     * Yêu cầu: người dùng là thành viên ACTIVE của CLB đó.
     * Không hiển thị Form của CLB khác.
     */
    public List<GoogleForm> getFormsByClub(Integer userId, Integer clubId) {
        clubPermissionService.requireCanView(userId, clubId);
        return googleFormRepository.findByClubId(clubId);
    }

    /**
     * Lấy chi tiết cấu trúc câu hỏi của Google Form.
     * Yêu cầu: người dùng là thành viên ACTIVE của CLB sở hữu Form đó.
     */
    public Map<String, Object> getFormDetails(Integer userId, Integer clubId, String formId) throws Exception {
        // Kiểm tra quyền xem
        clubPermissionService.requireCanView(userId, clubId);

        // Kiểm tra form thuộc CLB đang thao tác
        googleFormRepository.findByFormIdAndClubId(formId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Form này không thuộc CLB của bạn hoặc không tồn tại."));

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
     * Yêu cầu: người dùng là thành viên ACTIVE của CLB sở hữu Form đó.
     */
    public Map<String, Object> getFormResponses(Integer userId, Integer clubId, String formId) throws Exception {
        // Kiểm tra quyền xem
        clubPermissionService.requireCanView(userId, clubId);

        // Kiểm tra form thuộc CLB đang thao tác
        googleFormRepository.findByFormIdAndClubId(formId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Form này không thuộc CLB của bạn hoặc không tồn tại."));

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://forms.googleapis.com/v1/forms/" + formId + "/responses";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lấy danh sách phản hồi Google Form thất bại: " + response.getBody());
        }

        // Tự động đồng bộ các câu trả lời mới nhất sang Google Sheet liên kết
        try {
            syncFormResponsesToSheet(userId, clubId, formId);
        } catch (Exception e) {
            System.err.println("Tự động đồng bộ dữ liệu sang Google Sheet thất bại: " + e.getMessage());
        }

        return objectMapper.readValue(response.getBody(), Map.class);
    }

    /**
     * Tự động đồng bộ câu trả lời từ Google Form sang file Google Sheet liên kết.
     */
    @Transactional
    public String syncFormResponsesToSheet(Integer userId, Integer clubId, String formId) throws Exception {
        clubPermissionService.requireCanView(userId, clubId);

        GoogleForm googleForm = googleFormRepository.findByFormIdAndClubId(formId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Form này không thuộc CLB của bạn hoặc không tồn tại."));

        if (googleForm.getLinkedSpreadsheetId() == null) {
            throw new IllegalStateException("Google Form này chưa được liên kết với Google Sheet.");
        }

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 1. Lấy chi tiết Form để trích xuất thứ tự tiêu đề câu hỏi
        String formDetailsUrl = "https://forms.googleapis.com/v1/forms/" + formId;
        ResponseEntity<String> formResp = restTemplate.exchange(formDetailsUrl, HttpMethod.GET, entity, String.class);
        if (!formResp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Không thể lấy cấu trúc Google Form để đồng bộ.");
        }

        JsonNode formRoot = objectMapper.readTree(formResp.getBody());
        Map<String, String> questionMap = new LinkedHashMap<>();
        JsonNode itemsNode = formRoot.get("items");
        if (itemsNode != null && itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                if (item.has("questionItem")) {
                    String qTitle = item.has("title") ? item.get("title").asText() : "Câu hỏi";
                    JsonNode qNode = item.get("questionItem").get("question");
                    if (qNode != null && qNode.has("questionId")) {
                        questionMap.put(qNode.get("questionId").asText(), qTitle);
                    }
                }
            }
        }

        // 2. Lấy danh sách câu trả lời (responses)
        String responsesUrl = "https://forms.googleapis.com/v1/forms/" + formId + "/responses";
        ResponseEntity<String> responsesResp = restTemplate.exchange(responsesUrl, HttpMethod.GET, entity, String.class);
        if (!responsesResp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Không thể lấy dữ liệu phản hồi từ Google Form.");
        }

        JsonNode responsesRoot = objectMapper.readTree(responsesResp.getBody());
        JsonNode responsesArray = responsesRoot.get("responses");

        List<List<Object>> sheetData = new ArrayList<>();
        List<Object> headerRow = new ArrayList<>();
        headerRow.add("Thời gian gửi (Timestamp)");
        headerRow.addAll(questionMap.values());
        sheetData.add(headerRow);

        if (responsesArray != null && responsesArray.isArray()) {
            for (JsonNode responseNode : responsesArray) {
                List<Object> row = new ArrayList<>();
                String timestamp = responseNode.has("createTime") ? responseNode.get("createTime").asText() : "";
                row.add(timestamp);

                JsonNode answersNode = responseNode.get("answers");
                for (String questionId : questionMap.keySet()) {
                    String answerValue = "";
                    if (answersNode != null && answersNode.has(questionId)) {
                        JsonNode textAnswersNode = answersNode.get(questionId).get("textAnswers");
                        if (textAnswersNode != null && textAnswersNode.has("answers")) {
                            List<String> values = new ArrayList<>();
                            for (JsonNode ansVal : textAnswersNode.get("answers")) {
                                if (ansVal.has("value")) {
                                    values.add(ansVal.get("value").asText());
                                }
                            }
                            answerValue = String.join(", ", values);
                        }
                    }
                    row.add(answerValue);
                }
                sheetData.add(row);
            }
        }

        // 3. Ghi dữ liệu vào Google Sheet đã liên kết
        HttpHeaders sheetHeaders = new HttpHeaders();
        sheetHeaders.setContentType(MediaType.APPLICATION_JSON);
        sheetHeaders.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> sheetBody = new HashMap<>();
        sheetBody.put("range", "Sheet1!A1");
        sheetBody.put("majorDimension", "ROWS");
        sheetBody.put("values", sheetData);

        HttpEntity<Map<String, Object>> updateReq = new HttpEntity<>(sheetBody, sheetHeaders);
        String updateUrl = String.format(
                "https://sheets.googleapis.com/v4/spreadsheets/%s/values/Sheet1!A1?valueInputOption=USER_ENTERED",
                googleForm.getLinkedSpreadsheetId());

        ResponseEntity<String> updateResp = restTemplate.exchange(updateUrl, HttpMethod.PUT, updateReq, String.class);

        if (!updateResp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Cập nhật dữ liệu vào Google Sheet thất bại: " + updateResp.getBody());
        }

        return updateResp.getBody();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMMENT (thêm câu hỏi) — Mọi thành viên ACTIVE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thêm câu hỏi vào Google Form sử dụng batchUpdate.
     * Yêu cầu: người dùng là thành viên ACTIVE của CLB sở hữu Form đó.
     * (Cả PRESIDENT, TREASURER lẫn MEMBER đều có thể thêm câu hỏi)
     */
    @Transactional
    public String addQuestion(Integer userId, Integer clubId, String formId,
                              GoogleFormQuestionRequest questionRequest) throws Exception {
        // Kiểm tra quyền comment
        clubPermissionService.requireCanComment(userId, clubId);

        // Kiểm tra form thuộc CLB đang thao tác
        googleFormRepository.findByFormIdAndClubId(formId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Form này không thuộc CLB của bạn hoặc không tồn tại."));

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

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE TITLE — Chỉ PRESIDENT / TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật tên/tiêu đề của Google Form.
     * Yêu cầu: người dùng là PRESIDENT hoặc TREASURER của CLB sở hữu Form đó.
     */
    @Transactional
    public GoogleForm updateFormTitle(Integer userId, Integer clubId, String formId, String newTitle) throws Exception {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Tiêu đề không được để trống!");
        }

        // Kiểm tra quyền cập nhật
        clubPermissionService.requireCanWrite(userId, clubId);

        // Kiểm tra form thuộc CLB đang thao tác
        GoogleForm googleForm = googleFormRepository.findByFormIdAndClubId(formId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Form này không thuộc CLB của bạn hoặc không tồn tại."));

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> updateFormInfo = new HashMap<>();
        Map<String, Object> info = new HashMap<>();
        info.put("title", newTitle.trim());
        updateFormInfo.put("info", info);
        updateFormInfo.put("updateMask", "title");

        Map<String, Object> requestItem = new HashMap<>();
        requestItem.put("updateFormInfo", updateFormInfo);

        Map<String, Object> body = new HashMap<>();
        body.put("requests", List.of(requestItem));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://forms.googleapis.com/v1/forms/" + formId + ":batchUpdate";

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Cập nhật tiêu đề Google Form thất bại: " + response.getBody());
        }

        googleForm.setTitle(newTitle.trim());
        return googleFormRepository.save(googleForm);
    }

    /**
     * Cập nhật loại (type: EVENT hoặc CLUB_ACTIVITIES) của Google Form.
     * Yêu cầu: người dùng là PRESIDENT hoặc TREASURER của CLB sở hữu Form đó.
     */
    @Transactional
    public GoogleForm updateFormType(Integer userId, Integer clubId, String formId, SheetFormType newType) throws Exception {
        if (newType == null) {
            throw new IllegalArgumentException("Loại (type) không được để trống! Vui lòng chọn EVENT hoặc CLUB_ACTIVITIES.");
        }

        // Kiểm tra quyền cập nhật
        clubPermissionService.requireCanWrite(userId, clubId);

        // Kiểm tra form thuộc CLB đang thao tác
        GoogleForm googleForm = googleFormRepository.findByFormIdAndClubId(formId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Form này không thuộc CLB của bạn hoặc không tồn tại."));

        googleForm.setType(newType);

        // Đồng bộ loại cho Google Sheet liên kết nếu có
        if (googleForm.getLinkedSpreadsheetId() != null) {
            googleSheetRepository.findBySpreadsheetIdAndClubId(googleForm.getLinkedSpreadsheetId(), clubId)
                    .ifPresent(sheet -> {
                        sheet.setType(newType);
                        googleSheetRepository.save(sheet);
                    });
        }

        return googleFormRepository.save(googleForm);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE — Chỉ PRESIDENT / TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xóa Google Form khỏi hệ thống và Google Drive.
     * Yêu cầu: người dùng là PRESIDENT hoặc TREASURER của CLB sở hữu Form đó.
     */
    @Transactional
    public void deleteForm(Integer userId, Integer clubId, String formId) throws Exception {
        // Kiểm tra quyền xóa
        clubPermissionService.requireCanDelete(userId, clubId);

        // Lấy form và kiểm tra thuộc CLB này
        GoogleForm googleForm = googleFormRepository.findByFormIdAndClubId(formId, clubId)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy biểu mẫu trong CLB này hoặc bạn không có quyền xóa!"));

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

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

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy methods (giữ để không break code khác nếu có)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @deprecated Dùng {@link #getFormsByClub(Integer, Integer)} thay thế.
     */
    @Deprecated
    public List<GoogleForm> getFormsByUser(Integer userId) {
        return googleFormRepository.findByUserUserId(userId);
    }
}
