package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.GoogleAccount;
import com.example.clubmanagement.Entity.GoogleSheet;
import com.example.clubmanagement.Entity.SheetFormType;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.GoogleAccountRepository;
import com.example.clubmanagement.Repository.GoogleSheetRepository;
import com.example.clubmanagement.Repository.UserRepository;
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
public class GoogleSheetsService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    private final GoogleAccountRepository googleAccountRepository;
    private final GoogleSheetRepository googleSheetRepository;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final ClubPermissionService clubPermissionService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleSheetsService(GoogleAccountRepository googleAccountRepository,
                               GoogleSheetRepository googleSheetRepository,
                               GoogleCalendarService googleCalendarService,
                               UserRepository userRepository,
                               ClubRepository clubRepository,
                               ClubPermissionService clubPermissionService) {
        this.googleAccountRepository = googleAccountRepository;
        this.googleSheetRepository = googleSheetRepository;
        this.googleCalendarService = googleCalendarService;
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.clubPermissionService = clubPermissionService;
    }

    /**
     * Lấy URL để kết nối tài khoản Google với đầy đủ Scope cho Sheets và Drive File.
     */
    public String getAuthorizeUrl(Integer userId) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file")
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
     * Tạo một Google Sheet mới trong môi trường CLB chỉ định.
     * Yêu cầu: người dùng là PRESIDENT hoặc TREASURER của CLB đó.
     *
     * @param userId  ID người dùng thực hiện thao tác
     * @param clubId  ID của CLB mà Sheet được tạo trong đó
     * @param title   Tiêu đề Google Sheet
     * @param type    Loại sheet: EVENT hoặc CLUB_ACTIVITIES
     */
    @Transactional
    public GoogleSheet createSheet(Integer userId, Integer clubId, String title, SheetFormType type) throws Exception {
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

        // Gọi Google Sheets API để tạo file spreadsheet
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", title);
        body.put("properties", properties);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://sheets.googleapis.com/v4/spreadsheets",
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Tạo Google Sheet thất bại: " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        String spreadsheetId = jsonNode.get("spreadsheetId").asText();
        String spreadsheetUrl = jsonNode.has("spreadsheetUrl")
                ? jsonNode.get("spreadsheetUrl").asText()
                : "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/edit";

        GoogleSheet googleSheet = GoogleSheet.builder()
                .spreadsheetId(spreadsheetId)
                .title(title)
                .type(type)
                .spreadsheetUrl(spreadsheetUrl)
                .user(user)
                .club(club)
                .build();

        return googleSheetRepository.save(googleSheet);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ — Mọi thành viên ACTIVE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách Google Sheet thuộc CLB chỉ định.
     * Yêu cầu: người dùng là thành viên ACTIVE của CLB đó.
     * Không hiển thị Sheet của CLB khác.
     */
    public List<GoogleSheet> getSheetsByClub(Integer userId, Integer clubId) {
        clubPermissionService.requireCanView(userId, clubId);
        return googleSheetRepository.findByClubId(clubId);
    }

    /**
     * Đọc dữ liệu từ một vùng nhất định trong Google Sheet.
     * Yêu cầu: người dùng là thành viên ACTIVE của CLB sở hữu Sheet đó.
     */
    public List<List<Object>> getSheetValues(Integer userId, Integer clubId,
                                             String spreadsheetId, String range) throws Exception {
        // Kiểm tra quyền xem
        clubPermissionService.requireCanView(userId, clubId);

        // Kiểm tra sheet thuộc CLB đang thao tác
        googleSheetRepository.findBySpreadsheetIdAndClubId(spreadsheetId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Sheet này không thuộc CLB của bạn hoặc không tồn tại."));

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = String.format("https://sheets.googleapis.com/v4/spreadsheets/%s/values/%s",
                spreadsheetId, range);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Đọc dữ liệu Google Sheet thất bại: " + response.getBody());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode valuesNode = root.get("values");
        List<List<Object>> values = new ArrayList<>();

        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode rowNode : valuesNode) {
                List<Object> row = new ArrayList<>();
                if (rowNode.isArray()) {
                    for (JsonNode cellNode : rowNode) {
                        row.add(cellNode.asText());
                    }
                }
                values.add(row);
            }
        }
        return values;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE (ghi dữ liệu) — Chỉ PRESIDENT / TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ghi hoặc cập nhật dữ liệu vào Google Sheet.
     * Yêu cầu: người dùng là PRESIDENT hoặc TREASURER của CLB.
     */
    public String updateSheetValues(Integer userId, Integer clubId,
                                    String spreadsheetId, String range,
                                    List<List<Object>> values) throws Exception {
        // Kiểm tra quyền ghi
        clubPermissionService.requireCanWrite(userId, clubId);

        // Kiểm tra sheet thuộc CLB đang thao tác
        googleSheetRepository.findBySpreadsheetIdAndClubId(spreadsheetId, clubId)
                .orElseThrow(() -> new SecurityException(
                        "File Google Sheet này không thuộc CLB của bạn hoặc không tồn tại."));

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(activeAccount.getAccessToken());

        Map<String, Object> body = new HashMap<>();
        body.put("range", range);
        body.put("majorDimension", "ROWS");
        body.put("values", values);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = String.format(
                "https://sheets.googleapis.com/v4/spreadsheets/%s/values/%s?valueInputOption=USER_ENTERED",
                spreadsheetId, range);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Cập nhật dữ liệu Google Sheet thất bại: " + response.getBody());
        }

        return response.getBody();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE — Chỉ PRESIDENT / TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xóa Google Sheet khỏi hệ thống và Google Drive.
     * Yêu cầu: người dùng là PRESIDENT hoặc TREASURER của CLB sở hữu Sheet đó.
     */
    @Transactional
    public void deleteSheet(Integer userId, Integer clubId, String spreadsheetId) throws Exception {
        // Kiểm tra quyền xóa
        clubPermissionService.requireCanDelete(userId, clubId);

        // Lấy sheet và kiểm tra thuộc CLB này
        GoogleSheet googleSheet = googleSheetRepository.findBySpreadsheetIdAndClubId(spreadsheetId, clubId)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy file sheet trong CLB này hoặc bạn không có quyền xóa!"));

        GoogleAccount activeAccount = getActiveGoogleAccount(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(activeAccount.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = String.format("https://www.googleapis.com/drive/v3/files/%s", spreadsheetId);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode() != HttpStatus.NOT_FOUND) {
                System.err.println("Xóa file Google Drive thất bại: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi kết nối Google Drive API để xóa file: " + e.getMessage());
        }

        googleSheetRepository.delete(googleSheet);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy methods (giữ để không break code khác nếu có)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @deprecated Dùng {@link #getSheetsByClub(Integer, Integer)} thay thế.
     */
    @Deprecated
    public List<GoogleSheet> getSheetsByUser(Integer userId) {
        return googleSheetRepository.findByUserUserId(userId);
    }
}
