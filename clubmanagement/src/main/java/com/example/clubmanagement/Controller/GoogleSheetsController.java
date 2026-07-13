package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.GoogleSheet;
import com.example.clubmanagement.Entity.SheetFormType;
import com.example.clubmanagement.Service.GoogleSheetsService;
import com.example.clubmanagement.dto.GoogleSheetResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/google/sheets")
public class GoogleSheetsController {

    private final GoogleSheetsService googleSheetsService;

    public GoogleSheetsController(GoogleSheetsService googleSheetsService) {
        this.googleSheetsService = googleSheetsService;
    }

    /**
     * Lấy URL kết nối tài khoản Google có quyền truy cập Google Sheets và Drive.
     * GET /api/google/sheets/connect?userId=1
     */
   /** @GetMapping("/connect")
    public ResponseEntity<?> connect(@RequestParam Integer userId) {
        try {
            String url = googleSheetsService.getAuthorizeUrl(userId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Tạo mới một Google Sheet cho người dùng và lưu vào database hệ thống.
     * POST /api/google/sheets/create?userId=1&title=TenSheet&type=EVENT
     * type bắt buộc: EVENT hoặc CLUB_ACTIVITIES
     */
    @PostMapping("/create")
    public ResponseEntity<?> createSheet(@RequestParam Integer userId,
                                         @RequestParam String title,
                                         @RequestParam SheetFormType type) {
        try {
            GoogleSheet sheet = googleSheetsService.createSheet(userId, title, type);
            return ResponseEntity.ok(mapToResponse(sheet));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách các file sheet được tạo trong hệ thống của người dùng.
     * GET /api/google/sheets/list?userId=1
     */
    @GetMapping("/list")
    public ResponseEntity<?> getSheets(@RequestParam Integer userId) {
        try {
            List<GoogleSheetResponse> sheets = googleSheetsService.getSheetsByUser(userId).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(sheets);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Đọc dữ liệu từ Google Sheet tại vùng chỉ định.
     * GET /api/google/sheets/values?userId=1&spreadsheetId=...&range=Sheet1!A1:D10
     */
    @GetMapping("/values")
    public ResponseEntity<?> getSheetValues(@RequestParam Integer userId,
                                            @RequestParam String spreadsheetId,
                                            @RequestParam String range) {
        try {
            List<List<Object>> values = googleSheetsService.getSheetValues(userId, spreadsheetId, range);
            return ResponseEntity.ok(values);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Cập nhật dữ liệu Google Sheet tại vùng chỉ định.
     * PUT /api/google/sheets/values?userId=1&spreadsheetId=...&range=Sheet1!A1:D10
     * Body: [["Họ Tên", "Chức Vụ"], ["Nguyễn Văn A", "Trưởng Ban"]]
     */
    @PutMapping("/values")
    public ResponseEntity<?> updateSheetValues(@RequestParam Integer userId,
                                               @RequestParam String spreadsheetId,
                                               @RequestParam String range,
                                               @RequestBody List<List<Object>> values) {
        try {
            String response = googleSheetsService.updateSheetValues(userId, spreadsheetId, range, values);
            return ResponseEntity.ok(Map.of("message", "Cập nhật dữ liệu thành công!", "response", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Xóa Google Sheet khỏi hệ thống và Drive cá nhân của người dùng.
     * DELETE /api/google/sheets/{spreadsheetId}?userId=1
     */
    @DeleteMapping("/{spreadsheetId}")
    public ResponseEntity<?> deleteSheet(@RequestParam Integer userId,
                                         @PathVariable String spreadsheetId) {
        try {
            googleSheetsService.deleteSheet(userId, spreadsheetId);
            return ResponseEntity.ok(Map.of("message", "Xóa Google Sheet thành công!"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private GoogleSheetResponse mapToResponse(GoogleSheet sheet) {
        if (sheet == null) return null;
        return GoogleSheetResponse.builder()
                .id(sheet.getId())
                .spreadsheetId(sheet.getSpreadsheetId())
                .title(sheet.getTitle())
                .type(sheet.getType())
                .spreadsheetUrl(sheet.getSpreadsheetUrl())
                .userId(sheet.getUser() != null ? sheet.getUser().getUserId() : null)
                .createdAt(sheet.getCreatedAt())
                .updatedAt(sheet.getUpdatedAt())
                .build();
    }
}
