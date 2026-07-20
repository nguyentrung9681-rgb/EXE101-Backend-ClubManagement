package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.GoogleSheet;
import com.example.clubmanagement.Entity.SheetFormType;
import com.example.clubmanagement.Service.GoogleSheetsService;
import com.example.clubmanagement.dto.GoogleSheetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller quản lý Google Sheet theo môi trường CLB.
 *
 * <p>Phân quyền:
 * <ul>
 *   <li>Mọi request đều yêu cầu userId đã liên kết Google Account.</li>
 *   <li>Mọi request đều yêu cầu userId là thành viên ACTIVE của clubId.</li>
 *   <li>Tạo / Ghi / Xóa: chỉ PRESIDENT hoặc TREASURER.</li>
 *   <li>Xem danh sách / Đọc dữ liệu: mọi thành viên ACTIVE.</li>
 * </ul>
 */
@Tag(name = "Google Sheets", description = "Quản lý Google Sheet theo CLB với phân quyền RBAC")
@RestController
@RequestMapping("/api/google/sheets")
public class GoogleSheetsController {

    private final GoogleSheetsService googleSheetsService;

    public GoogleSheetsController(GoogleSheetsService googleSheetsService) {
        this.googleSheetsService = googleSheetsService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/google/sheets/create
    // Yêu cầu: PRESIDENT hoặc TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo mới một Google Sheet cho CLB.
     * Chỉ PRESIDENT hoặc TREASURER của CLB đó mới được thực hiện.
     *
     * POST /api/google/sheets/create?userId=1&clubId=10&title=TenSheet&type=EVENT
     */
    @Operation(summary = "Tạo Google Sheet mới trong CLB",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền tạo.")
    @PostMapping("/create")
    public ResponseEntity<?> createSheet(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "Tiêu đề sheet") @RequestParam String title,
            @Parameter(description = "Loại: EVENT hoặc CLUB_ACTIVITIES") @RequestParam SheetFormType type) {
        try {
            GoogleSheet sheet = googleSheetsService.createSheet(userId, clubId, title, type);
            return ResponseEntity.ok(mapToResponse(sheet));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/google/sheets/list
    // Yêu cầu: mọi thành viên ACTIVE của CLB
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách Google Sheet thuộc CLB.
     * Chỉ hiển thị sheet của CLB đó — không lộ sheet của CLB khác.
     *
     * GET /api/google/sheets/list?userId=1&clubId=10
     */
    @Operation(summary = "Lấy danh sách Google Sheet của CLB",
               description = "Mọi thành viên ACTIVE được xem. Chỉ thấy sheet trong CLB mình.")
    @GetMapping("/list")
    public ResponseEntity<?> getSheets(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId) {
        try {
            List<GoogleSheetResponse> sheets = googleSheetsService.getSheetsByClub(userId, clubId)
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(sheets);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/google/sheets/values
    // Yêu cầu: mọi thành viên ACTIVE của CLB
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đọc dữ liệu từ Google Sheet tại vùng chỉ định.
     * Sheet phải thuộc CLB mà người dùng đang hoạt động.
     *
     * GET /api/google/sheets/values?userId=1&clubId=10&spreadsheetId=...&range=Sheet1!A1:D10
     */
    @Operation(summary = "Đọc dữ liệu từ Google Sheet",
               description = "Mọi thành viên ACTIVE được đọc. Sheet phải thuộc CLB của người dùng.")
    @GetMapping("/values")
    public ResponseEntity<?> getSheetValues(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID spreadsheet Google") @RequestParam String spreadsheetId,
            @Parameter(description = "Vùng đọc dữ liệu, vd: Sheet1!A1:D10") @RequestParam String range) {
        try {
            List<List<Object>> values = googleSheetsService.getSheetValues(userId, clubId, spreadsheetId, range);
            return ResponseEntity.ok(values);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/google/sheets/values
    // Yêu cầu: PRESIDENT hoặc TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật dữ liệu Google Sheet tại vùng chỉ định.
     * Chỉ PRESIDENT hoặc TREASURER của CLB mới được ghi dữ liệu.
     *
     * PUT /api/google/sheets/values?userId=1&clubId=10&spreadsheetId=...&range=Sheet1!A1:D10
     * Body: [["Họ Tên", "Chức Vụ"], ["Nguyễn Văn A", "Trưởng Ban"]]
     */
    @Operation(summary = "Cập nhật dữ liệu vào Google Sheet",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền ghi dữ liệu.")
    @PutMapping("/values")
    public ResponseEntity<?> updateSheetValues(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID spreadsheet Google") @RequestParam String spreadsheetId,
            @Parameter(description = "Vùng ghi dữ liệu, vd: Sheet1!A1:D10") @RequestParam String range,
            @RequestBody List<List<Object>> values) {
        try {
            String response = googleSheetsService.updateSheetValues(userId, clubId, spreadsheetId, range, values);
            return ResponseEntity.ok(Map.of("message", "Cập nhật dữ liệu thành công!", "response", response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/google/sheets/{spreadsheetId}
    // Yêu cầu: PRESIDENT hoặc TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xóa Google Sheet khỏi hệ thống và Google Drive.
     * Chỉ PRESIDENT hoặc TREASURER của CLB mới được xóa.
     *
     * DELETE /api/google/sheets/{spreadsheetId}?userId=1&clubId=10
     */
    @Operation(summary = "Xóa Google Sheet",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền xóa.")
    @DeleteMapping("/{spreadsheetId}")
    public ResponseEntity<?> deleteSheet(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID spreadsheet Google") @PathVariable String spreadsheetId) {
        try {
            googleSheetsService.deleteSheet(userId, clubId, spreadsheetId);
            return ResponseEntity.ok(Map.of("message", "Xóa Google Sheet thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper mapper
    // ─────────────────────────────────────────────────────────────────────────

    private GoogleSheetResponse mapToResponse(GoogleSheet sheet) {
        if (sheet == null) return null;
        return GoogleSheetResponse.builder()
                .id(sheet.getId())
                .spreadsheetId(sheet.getSpreadsheetId())
                .title(sheet.getTitle())
                .type(sheet.getType())
                .spreadsheetUrl(sheet.getSpreadsheetUrl())
                .userId(sheet.getUser() != null ? sheet.getUser().getUserId() : null)
                .clubId(sheet.getClub() != null ? sheet.getClub().getId() : null)
                .createdAt(sheet.getCreatedAt())
                .updatedAt(sheet.getUpdatedAt())
                .build();
    }
}
