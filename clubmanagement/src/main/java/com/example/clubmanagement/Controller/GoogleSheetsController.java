package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
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
 * Controller quản lý Google Sheet theo môi trường CLB với phân quyền RBAC và kiểm tra JWT.
 */
@Tag(name = "Google Sheets", description = "Quản lý Google Sheet theo CLB với phân quyền RBAC")
@RestController
@RequestMapping("/api/google/sheets")
public class GoogleSheetsController {

    private final GoogleSheetsService googleSheetsService;

    public GoogleSheetsController(GoogleSheetsService googleSheetsService) {
        this.googleSheetsService = googleSheetsService;
    }

    @Operation(summary = "Tạo Google Sheet mới trong CLB",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền tạo.")
    @PostMapping("/create")
    public ResponseEntity<?> createSheet(
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "Tiêu đề sheet") @RequestParam String title,
            @Parameter(description = "Loại: EVENT hoặc CLUB_ACTIVITIES") @RequestParam SheetFormType type) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GoogleSheet sheet = googleSheetsService.createSheet(effectiveUserId, clubId, title, type);
            return ResponseEntity.ok(mapToResponse(sheet));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy danh sách Google Sheet của CLB",
               description = "Mọi thành viên ACTIVE được xem. Chỉ thấy sheet trong CLB mình.")
    @GetMapping("/list")
    public ResponseEntity<?> getSheets(
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            List<GoogleSheetResponse> sheets = googleSheetsService.getSheetsByClub(effectiveUserId, clubId)
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(sheets);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Đọc dữ liệu từ Google Sheet",
               description = "Mọi thành viên ACTIVE được đọc. Sheet phải thuộc CLB của người dùng.")
    @GetMapping("/values")
    public ResponseEntity<?> getSheetValues(
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID spreadsheet Google") @RequestParam String spreadsheetId,
            @Parameter(description = "Vùng đọc dữ liệu, vd: Sheet1!A1:D10") @RequestParam String range) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            List<List<Object>> values = googleSheetsService.getSheetValues(effectiveUserId, clubId, spreadsheetId, range);
            return ResponseEntity.ok(values);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật dữ liệu vào Google Sheet",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền ghi dữ liệu.")
    @PutMapping("/values")
    public ResponseEntity<?> updateSheetValues(
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID spreadsheet Google") @RequestParam String spreadsheetId,
            @Parameter(description = "Vùng ghi dữ liệu, vd: Sheet1!A1:D10") @RequestParam String range,
            @RequestBody List<List<Object>> values) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            String response = googleSheetsService.updateSheetValues(effectiveUserId, clubId, spreadsheetId, range, values);
            return ResponseEntity.ok(Map.of("message", "Cập nhật dữ liệu thành công!", "response", response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật tiêu đề Google Sheet",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền cập nhật tiêu đề.")
    @PutMapping("/{spreadsheetId}/title")
    public ResponseEntity<?> updateSheetTitle(
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String spreadsheetId,
            @Parameter(description = "Tiêu đề mới của sheet") @RequestParam String title) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GoogleSheet sheet = googleSheetsService.updateSheetTitle(effectiveUserId, clubId, spreadsheetId, title);
            return ResponseEntity.ok(mapToResponse(sheet));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật loại của Google Sheet",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền cập nhật loại (EVENT hoặc CLUB_ACTIVITIES).")
    @PutMapping("/{spreadsheetId}/type")
    public ResponseEntity<?> updateSheetType(
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String spreadsheetId,
            @Parameter(description = "Loại mới: EVENT hoặc CLUB_ACTIVITIES") @RequestParam SheetFormType type) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GoogleSheet sheet = googleSheetsService.updateSheetType(effectiveUserId, clubId, spreadsheetId, type);
            return ResponseEntity.ok(mapToResponse(sheet));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa Google Sheet",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền xóa.")
    @DeleteMapping("/{spreadsheetId}")
    public ResponseEntity<?> deleteSheet(
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID spreadsheet Google") @PathVariable String spreadsheetId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            googleSheetsService.deleteSheet(effectiveUserId, clubId, spreadsheetId);
            return ResponseEntity.ok(Map.of("message", "Xóa Google Sheet thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
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
                .clubId(sheet.getClub() != null ? sheet.getClub().getId() : null)
                .createdAt(sheet.getCreatedAt())
                .updatedAt(sheet.getUpdatedAt())
                .build();
    }
}
