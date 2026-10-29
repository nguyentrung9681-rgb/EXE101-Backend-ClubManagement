# TÀI LIỆU KẾT NỐI API (API INTEGRATION DOCUMENT)
*Dự án: Club Management System (Backend)*

Tài liệu này dùng để thống nhất các chuẩn kết nối, cấu trúc dữ liệu Request/Response và các luồng API chính giữa Backend (BE) và Frontend (FE).

---

## 1. QUY CHUẨN CHUNG (GENERAL STANDARDS)

### 1.1. Base URL
* **Môi trường Phát triển (Local):** `http://localhost:8080` (hoặc cấu hình trong file `.env`)
* **Môi trường Staging/Production:** *(Sẽ bổ sung khi deploy)*

### 1.2. HTTP Status Code sử dụng
Tất cả các API cần sử dụng đúng mã HTTP Status Code:
* `200 OK`: Request được xử lý thành công, dữ liệu trả về nằm trong body.
* `201 Created`: Tạo mới thành công (thường dùng cho POST).
* `400 Bad Request`: Request không hợp lệ (lỗi cú pháp JSON, thiếu tham số bắt buộc, validation thất bại).
* `401 Unauthorized`: FE chưa gửi JWT Token hoặc Token hết hạn/không hợp lệ.
* `403 Forbidden`: Người dùng đã đăng nhập nhưng không có quyền thực hiện chức năng này.
* `404 Not Found`: Không tìm thấy tài nguyên yêu cầu (ví dụ: ID câu lạc bộ hoặc ID sự kiện không tồn tại).
* `500 Internal Server Error`: Lỗi phát sinh từ phía server.

---

## 2. LUỒNG NGHIỆP VỤ CHÍNH (KEY WORKFLOWS)

Dưới đây là sơ đồ/trình tự gọi API để FE hình dung cách ghép nối:

### Luồng Quản lý Câu Lạc Bộ & Sự Kiện (Club & Event Lifecycle)
1. **Đăng ký/Đăng nhập:** FE gọi `POST /api/auth/register` -> `POST /api/auth/login` để lấy JWT Token.
2. **Cung cấp Token:** Với mọi API tiếp theo, FE truyền Token vào Header `Authorization: Bearer <token>`.
3. **Tạo Câu Lạc Bộ:** Người dùng tạo CLB bằng cách gọi `POST /api/clubs?userId={userId}`.
4. **Tạo Sự Kiện:** Người dùng tạo sự kiện cho CLB đó bằng cách gọi `POST /api/events?clubId={clubId}&userId={userId}`.
5. **Đồng bộ Lịch Google:** Để đồng bộ sự kiện lên Google Calendar:
   * FE chuyển hướng người dùng sang URL nhận từ `GET /api/google/connect?userId={userId}` để xin quyền từ Google.
   * Google tự động callback về BE thông qua `GET /api/google/callback` để hoàn tất liên kết.

---

## 3. DANH SÁCH API CHI TIẾT (API REFERENCE)

### 3.1. HỆ THỐNG XÁC THỰC (AUTHENTICATION)

#### 3.1.1. Đăng ký tài khoản Local (Register)
* **URL:** `/api/auth/register`
* **Method:** `POST`
* **Request Headers:**
  * `Content-Type: application/json`

* **Request Body:**
```json
{
  "email": "nguyenvana@example.com",
  "password": "Password123",
  "fullName": "Nguyễn Văn A"
}
```

* **Responses:**
  * **200 OK (Thành công):**
    ```text
    Đăng ký tài khoản thành công!
    ```
  * **400 Bad Request (Thất bại - trùng Email hoặc lỗi logic):**
    ```text
    Email đã được sử dụng!
    ```

#### 3.1.2. Đăng nhập hệ thống (Login)
* **URL:** `/api/auth/login`
* **Method:** `POST`
* **Request Body:**
```json
{
  "email": "nguyenvana@example.com",
  "password": "Password123"
}
```

* **Responses:**
  * **200 OK (Thành công):**
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "userId": 1,
      "fullName": "Nguyễn Văn A",
      "username": "nguyenvana",
      "email": "nguyenvana@example.com",
      "authProvider": "LOCAL",
      "lastSelectedClubId": null,
      "role": "USER",
      "message": null
    }
    ```
  * **400 Bad Request (Sai thông tin đăng nhập):**
    ```text
    Sai tài khoản hoặc mật khẩu!
    ```

#### 3.1.3. Đăng ký / Đăng nhập bằng Google (Gmail)
* **URL:** `/api/auth/google`
* **Method:** `POST`
* **Request Headers:**
  * `Content-Type: application/json`

* **Request Body:**
```json
{
  "googleId": "109238409128309123456",
  "email": "nguyenvana@gmail.com",
  "fullName": "Nguyễn Văn A",
  "avatarUrl": "https://lh3.googleusercontent.com/a/ac..."
}
```

* **Responses:**
  * **200 OK (Thành công - Đăng ký hoặc Đăng nhập tự động):**
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "userId": 1,
      "fullName": "Nguyễn Văn A",
      "username": "nguyenvana",
      "email": "nguyenvana@gmail.com",
      "authProvider": "GOOGLE",
      "lastSelectedClubId": null,
      "role": "USER",
      "message": "Đăng nhập/Đăng ký bằng Google thành công!"
    }
    ```
  * **400 Bad Request (Tài khoản bị khóa hoặc thiếu dữ liệu):**
    ```text
    Tài khoản này đã bị khóa hoặc không hoạt động!
    ```

#### 3.1.4. Đăng xuất hệ thống (Logout)
* **URL:** `/api/auth/logout`
* **Method:** `POST`
* **Response (200 OK):**
```json
{
  "message": "Đăng xuất thành công"
}
```

---

### 3.2. QUẢN LÝ CÂU LẠC BỘ (CLUBS)

#### 3.2.1. Tạo Câu Lạc Bộ mới
* **URL:** `/api/clubs`
* **Method:** `POST`
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `userId` | `Integer` | Có | ID của người dùng tạo câu lạc bộ |

* **Request Body:**
```json
{
  "name": "CLB Bóng Đá",
  "description": "Câu lạc bộ giao lưu bóng đá của trường",
  "logoUrl": "https://example.com/logo.png"
}
```

* **Response (200 OK):**
```json
{
  "id": 1,
  "name": "CLB Bóng Đá",
  "description": "Câu lạc bộ giao lưu bóng đá của trường",
  "logoUrl": "https://example.com/logo.png",
  "status": "ACTIVE",
  "createdByUserId": 1,
  "createdByName": "Nguyễn Văn A",
  "createdAt": "2026-07-07T10:00:00",
  "updatedAt": "2026-07-07T10:00:00"
}
```

#### 3.2.2. Lấy danh sách tất cả Câu Lạc Bộ
* **URL:** `/api/clubs`
* **Method:** `GET`
* **Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "CLB Bóng Đá",
    "description": "Câu lạc bộ giao lưu bóng đá của trường",
    "logoUrl": "https://example.com/logo.png",
    "status": "ACTIVE",
    "createdByUserId": 1,
    "createdByName": "Nguyễn Văn A",
    "createdAt": "2026-07-07T10:00:00",
    "updatedAt": "2026-07-07T10:00:00"
  }
]
```

#### 3.2.3. Lấy chi tiết Câu Lạc Bộ theo ID
* **URL:** `/api/clubs/{id}`
* **Method:** `GET`
* **Path Variables:**
  * `id` (Integer): ID của câu lạc bộ cần lấy thông tin.
* **Response (200 OK):**
```json
{
  "id": 1,
  "name": "CLB Bóng Đá",
  "description": "Câu lạc bộ giao lưu bóng đá của trường",
  "logoUrl": "https://example.com/logo.png",
  "status": "ACTIVE",
  "createdByUserId": 1,
  "createdByName": "Nguyễn Văn A",
  "createdAt": "2026-07-07T10:00:00",
  "updatedAt": "2026-07-07T10:00:00"
}
```

#### 3.2.4. Xóa Câu Lạc Bộ theo ID
* **URL:** `/api/clubs/{id}`
* **Method:** `DELETE`
* **Path Variables:**
  * `id` (Integer): ID của câu lạc bộ cần xóa.
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `userId` | `Integer` | Không | ID của người dùng thực hiện xóa (Nếu không truyền, tự động lấy từ JWT Token) |

* **Phân quyền:** Chỉ **người tạo ra câu lạc bộ (`createdBy`)** hoặc **người được trao quyền xóa (`deletionPermittedUser`)** mới có quyền xóa câu lạc bộ này.
* **Response (200 OK):**
```json
{
  "message": "Xóa câu lạc bộ thành công!"
}
```
* **Response (403 Forbidden - Không có quyền xóa):**
```json
{
  "error": "Bạn không có quyền xóa câu lạc bộ này! Chỉ người tạo hoặc người được trao quyền xóa mới có thể xóa."
}
```

#### 3.2.5. Trao quyền xóa Câu Lạc Bộ cho người dùng khác
* **URL:** `/api/clubs/{id}/grant-deletion-permission`
* **Method:** `POST`
* **Path Variables:**
  * `id` (Integer): ID của câu lạc bộ.
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `targetUserId` | `Integer` | Có | ID của người dùng được trao quyền xóa CLB |
  | `userId` | `Integer` | Không | ID của người tạo CLB (Tự động lấy từ JWT nếu để trống) |

* **Phân quyền:** Chỉ **người tạo ra câu lạc bộ (`createdBy`)** mới có quyền trao quyền xóa.
* **Response (200 OK):**
```json
{
  "message": "Bạn đã trao quyền xóa câu lạc bộ cho Nguyễn Văn B thành công!",
  "club": {
    "id": 10,
    "name": "CLB Bóng Đá",
    "createdByUserId": 1,
    "deletionPermittedUsers": [
      {
        "userId": 2,
        "fullName": "Nguyễn Văn B",
        "email": "nguyenvanb@example.com"
      }
    ]
  }
}
```

#### 3.2.6. Thu hồi quyền xóa Câu Lạc Bộ
* **URL:** `/api/clubs/{id}/revoke-deletion-permission`
* **Method:** `POST`
* **Path Variables:**
  * `id` (Integer): ID của câu lạc bộ.
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `targetUserId` | `Integer` | Không | ID của người dùng bị thu hồi quyền xóa (Nếu để trống, thu hồi tất cả) |
  | `userId` | `Integer` | Không | ID của người tạo CLB (Tự động lấy từ JWT nếu để trống) |

* **Phân quyền:** Chỉ **người tạo ra câu lạc bộ (`createdBy`)** mới có quyền thu hồi quyền xóa.
* **Response (200 OK):**
```json
{
  "message": "Bạn đã thu hồi quyền xóa câu lạc bộ từ Nguyễn Văn B thành công!",
  "club": {
    "id": 10,
    "name": "CLB Bóng Đá",
    "createdByUserId": 1,
    "deletionPermittedUsers": []
  }
}
```

#### 3.2.7. Lấy danh sách người được trao quyền xóa Câu Lạc Bộ
* **URL:** `/api/clubs/{id}/deletion-permissions`
* **Method:** `GET`
* **Path Variables:**
  * `id` (Integer): ID của câu lạc bộ.
* **Response (200 OK):**
```json
[
  {
    "userId": 2,
    "fullName": "Nguyễn Văn B",
    "email": "nguyenvanb@example.com"
  },
  {
    "userId": 3,
    "fullName": "Trần Văn C",
    "email": "tranvanc@example.com"
  }
]
```

---

### 3.3. QUẢN LÝ SỰ KIỆN (EVENTS)

#### 3.3.1. Tạo Sự Kiện hoạt động mới
* **URL:** `/api/events`
* **Method:** `POST`
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `clubId` | `Integer` | Có | ID của Câu lạc bộ tổ chức |
  | `userId` | `Integer` | Có | ID của người dùng tạo sự kiện (Người quản lý CLB) |

* **Request Body:**
```json
{
  "title": "Giao hữu bóng đá tháng 7",
  "description": "Trận bóng đá giao hữu với CLB bạn tại Sân vận động trường",
  "startTime": "2026-07-10T15:00:00",
  "endTime": "2026-07-10T17:00:00",
  "location": "Sân vận động Bách Khoa"
}
```

* **Response (200 OK):**
```json
{
  "id": 5,
  "title": "Giao hữu bóng đá tháng 7",
  "description": "Trận bóng đá giao hữu với CLB bạn tại Sân vận động trường",
  "startTime": "2026-07-10T15:00:00",
  "endTime": "2026-07-10T17:00:00",
  "location": "Sân vận động Bách Khoa",
  "clubId": 1,
  "clubName": "CLB Bóng Đá"
}
```

---

### 3.4. LIÊN KẾT GOOGLE CALENDAR (GOOGLE SYNC)

#### 3.4.1. Lấy URL cấp quyền Google Calendar
* **URL:** `/api/google/connect`
* **Method:** `GET`
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `userId` | `Integer` | Có | ID của người dùng cần liên kết tài khoản Google |

* **Response (200 OK):**
```json
{
  "url": "https://accounts.google.com/o/oauth2/auth?client_id=...&redirect_uri=...&scope=...&state=1"
}
```
* **Mô tả hành động của FE:** FE nhận URL này và thực hiện chuyển hướng màn hình người dùng (`window.location.href = res.data.url`). Người dùng sẽ đồng ý cấp quyền trên giao diện của Google.

---

### 3.5. HỆ THỐNG CHAT NỘI BỘ (S-CLUB INTERNAL CHAT)

#### 3.5.1. Lấy danh sách tin nhắn Chat Phòng Ban (dành cho nút "Open Department Chat")
* **URL:** `/api/chat/clubs/{clubId}/departments/{departmentId}/messages?limit=50&beforeMessageId=123&requesterUserId={id}`
* **Method:** `GET`
* **Query Parameters:**
  - `limit`: Số tin nhắn cần lấy (Mặc định: 50)
  - `beforeMessageId`: ID tin nhắn cũ hơn để cuộn lên tải tiếp (Lazy loading). Để trống khi mở room lần đầu.
* **Response (200 OK):**
```json
{
  "messages": [
    {
      "id": 150,
      "clubId": 1,
      "departmentId": 5,
      "departmentName": "Social",
      "senderId": 2,
      "senderName": "Mỹ Anh",
      "senderAvatar": "http://...",
      "content": "Chào ban Truyền thông!",
      "messageType": "TEXT",
      "isPinned": false,
      "isDeleted": false,
      "createdAt": "2026-09-29T12:00:00",
      "attachments": []
    }
  ],
  "nextBeforeMessageId": 101,
  "hasMore": true,
  "limit": 50
}
```

#### 3.5.2. Gửi tin nhắn mới vào Phòng Ban
* **URL:** `/api/chat/clubs/{clubId}/departments/{departmentId}/messages?requesterUserId={id}`
* **Method:** `POST`
* **Request Body:**
```json
{
  "content": "Đây là tài liệu họp ban Social",
  "messageType": "TEXT",
  "attachments": []
}
```

#### 3.5.3. Lấy danh sách tin nhắn Kênh Chung CLB
* **URL:** `/api/chat/clubs/{clubId}/messages?limit=50&beforeMessageId=123&requesterUserId={id}`
* **Method:** `GET`

#### 3.5.4. Gửi tin nhắn mới vào Kênh Chung CLB
* **URL:** `/api/chat/clubs/{clubId}/messages?requesterUserId={id}`
* **Method:** `POST`

#### 3.5.5. Tải tệp tin đính kèm
* **URL:** `/api/chat/upload`
* **Method:** `POST` (`multipart/form-data`)

---

### 3.6. QUẢN LÝ GOOGLE FORMS & GOOGLE SHEETS

#### 3.6.1. Cập nhật tên / tiêu đề Google Form
* **URL:** `/api/google/forms/{formId}/title`
* **Method:** `PUT`
* **Phân quyền:** Chỉ `PRESIDENT` hoặc `TREASURER` của CLB sở hữu Form.
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `clubId` | `Integer` | Có | ID của CLB sở hữu Form |
  | `title` | `String` | Có | Tên/Tiêu đề mới cho Form |
  | `userId` | `Integer` | Không | ID người dùng (Tự động lấy từ JWT Token nếu để trống) |

#### 3.6.2. Cập nhật tên / tiêu đề Google Sheet
* **URL:** `/api/google/sheets/{spreadsheetId}/title`
* **Method:** `PUT`
* **Phân quyền:** Chỉ `PRESIDENT` hoặc `TREASURER` của CLB sở hữu Sheet.

---

### 3.7. THANH TOÁN GÓI DỊCH VỤ (PAYMENT & VIETQR PAYOS)

#### 3.7.1. Lấy danh sách gói dịch vụ khả dụng
* **URL:** `/api/packages`
* **Method:** `GET`
* **Response (200 OK):**
```json
[
  {
    "id": 1,
    "code": "STANDARD_MONTH",
    "name": "Gói Tiêu Chuẩn (1 Tháng)",
    "price": 100000.00,
    "durationDays": 30,
    "description": "Gói dùng thử trải nghiệm 30 ngày"
  }
]
```

#### 3.7.2. Kiểm tra quyền thanh toán của người dùng
* **URL:** `/api/payments/check-permission`
* **Method:** `GET`
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `clubId` | `Integer` | Có | ID của CLB |
  | `userId` | `Integer` | Có | ID của người dùng |
* **Response (200 OK):**
```json
{
  "canPay": true,
  "clubId": 1,
  "userId": 5
}
```

#### 3.7.3. Tạo đơn hàng thanh toán VietQR (Create Checkout Order)
* **URL:** `/api/payments/create-checkout`
* **Method:** `POST`
* **Request Body:**
```json
{
  "packageId": 1,
  "clubId": 1,
  "userId": 5
}
```
* **Response (200 OK):**
```json
{
  "id": 10,
  "orderCode": 1727850000123,
  "userId": 5,
  "clubId": 1,
  "packageId": 1,
  "packageName": "Gói Tiêu Chuẩn (1 Tháng)",
  "amount": 100000.00,
  "status": "PENDING",
  "checkoutUrl": "https://pay.payos.vn/web/...",
  "qrCodeUrl": "https://img.vietqr.io/image/...",
  "paymentMethod": "VIETQR",
  "createdAt": "2026-10-02T15:30:00"
}
```

#### 3.7.4. Kiểm tra trạng thái đơn hàng (Polling Order Status)
* **URL:** `/api/payments/order/{orderCode}`
* **Method:** `GET`
* **Response (200 OK):**
```json
{
  "id": 10,
  "orderCode": 1727850000123,
  "userId": 5,
  "clubId": 1,
  "packageId": 1,
  "packageName": "Gói Tiêu Chuẩn (1 Tháng)",
  "amount": 100000.00,
  "status": "PAID",
  "checkoutUrl": "https://pay.payos.vn/web/...",
  "qrCodeUrl": "https://img.vietqr.io/image/...",
  "paymentMethod": "VIETQR",
  "transactionNo": "FT240929123456",
  "createdAt": "2026-10-02T15:30:00",
  "paidAt": "2026-10-02T15:31:20"
}
```

#### 3.7.5. Hủy đơn hàng thanh toán
* **URL:** `/api/payments/order/{orderCode}/cancel`
* **Method:** `POST`

#### 3.7.6. Lấy gói dịch vụ đang hoạt động của CLB
* **URL:** `/api/payments/club/{clubId}/subscription`
* **Method:** `GET`
* **Response (200 OK):**
```json
{
  "id": 1,
  "clubId": 1,
  "clubName": "CLB Bóng Đá",
  "packageId": 1,
  "packageName": "Gói Tiêu Chuẩn (1 Tháng)",
  "startDate": "2026-10-02T15:31:20",
  "endDate": "2026-11-01T15:31:20",
  "status": "ACTIVE"
}
```

---

### 3.8. PHÂN TÍCH & ĐO LƯỜNG PHỄU CHUYỂN ĐỔI (ANALYTICS)

 Backend đã tự động ghi nhận 3 bước `signup`, `checkout_start`, `payment_success`.
 Frontend chỉ cần gửi 2 bước còn lại: `page_view` và `pricing_view`.

#### 3.8.1. Gửi sự kiện người dùng (public, không cần JWT)
* **URL:** `/api/analytics/events`
* **Method:** `POST`
* **Request Body:**
```json
{
  "eventName": "pricing_view",
  "sessionId": "8f2c9b3a-1e57-4d0c-9f4a-2b6d7c1e0a55",
  "path": "/pricing?utm_source=facebook&utm_medium=cpc&utm_campaign=oct2026",
  "referrer": "https://www.facebook.com/",
  "utmSource": "facebook",
  "utmMedium": "cpc",
  "utmCampaign": "oct2026",
  "utmContent": "banner-01"
}
```
* **Quy tắc tham số:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `eventName` | `String` | Có | Chỉ chữ thường, số, `_`, độ dài 2–60. Giá trị hợp lệ: `page_view`, `pricing_view` (2 sự kiện FE phải gửi) |
  | `sessionId` | `String` | Nên có | Mã định danh khách truy cập, lưu `localStorage`, dùng để đếm **visitor** (không trùng lặp) |
  | `path` | `String` | Không | Đường dẫn trang, tối đa 512 ký tự |
  | `referrer` | `String` | Không | Trang giới thiệu, tối đa 512 ký tự |
  | `utmSource` | `String` | Nên có | `facebook`, `tiktok`, `google`, `zalo`… — **bắt buộc nếu muốn báo cáo theo kênh mạng xã hội** |
  | `utmMedium` | `String` | Không | `cpc`, `social`, `organic`, `posting`… |
  | `utmCampaign` | `String` | Không | Tên chiến dịch |
  | `utmContent` | `String` | Không | Phiên bản nội dung quảng cáo |

* **Response (200 OK):**
```json
{"status": "recorded"}
```
Sự kiện sai định dạng (`eventName` chứa ký tự không hợp lệ) trả `400` kèm `{"message": "..."}`. FE nên gửi kiểu "fire-and-forget" (không chặn UI, không hiển thị lỗi).
* **Gợi ý triển khai FE:** gọi ngay khi trang tải, lấy `utm_*` từ `URLSearchParams` của lượt truy cập **đầu tiên** và lưu lại để các trang sau vẫn attribution đúng.

#### 3.8.2. Báo cáo phễu chuyển đổi + KPI doanh thu cho trang quản trị
* **URL:** `/api/admin/analytics`
* **Method:** `GET`
* **Yêu cầu xác thực:** `Bearer` token của tài khoản ADMIN
* **Query Parameters:**
  | Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
  | :--- | :--- | :--- | :--- |
  | `days` | `Integer` | Không | Số ngày gần nhất, mặc định 30, giới hạn 1–365 (dùng 365 để vẽ biểu đồ cả năm) |
* **Response (200 OK):**
```json
{
  "days": 30,
  "from": "2026-09-28T17:10:00",
  "to": "2026-10-28T17:10:00",
  "totals": {
    "trackedEvents": 482,
    "visitors": 139,
    "newUsers": 41,
    "checkouts": 18,
    "paidOrders": 12,
    "revenue": 1200000.00
  },
  "funnel": [
    {"step": "page_view", "label": "Lượt xem trang", "unit": "visitor", "count": 139, "conversionFromPrevious": null, "conversionFromStart": 100.0},
    {"step": "pricing_view", "label": "Xem bảng giá gói dịch vụ", "unit": "visitor", "count": 62, "conversionFromPrevious": 44.6, "conversionFromStart": 44.6},
    {"step": "signup", "label": "Đăng ký tài khoản mới", "unit": "user", "count": 41, "conversionFromPrevious": 66.1, "conversionFromStart": 29.5},
    {"step": "checkout_start", "label": "Bắt đầu thanh toán (tạo đơn)", "unit": "order", "count": 18, "conversionFromPrevious": 43.9, "conversionFromStart": 12.9},
    {"step": "payment_success", "label": "Thanh toán thành công", "unit": "order", "count": 12, "conversionFromPrevious": 66.7, "conversionFromStart": 8.6}
  ],
  "channels": [
    {"source": "facebook", "medium": "cpc", "visitors": 74, "checkouts": 11, "paidOrders": 8, "revenue": 800000.00},
    {"source": "tiktok", "medium": "organic", "visitors": 31, "checkouts": 5, "paidOrders": 3, "revenue": 300000.00}
  ],
  "revenue": {
    "weekly":  {"label": "7 ngày gần nhất", "from": "2026-10-21T17:10:00", "to": "2026-10-28T17:10:00", "amount": 297000.00, "paidOrders": 3, "previousAmount": 198000.00, "growthPercent": 50.00},
    "monthly": {"label": "30 ngày gần nhất", "from": "2026-09-28T17:10:00", "to": "2026-10-28T17:10:00", "amount": 1200000.00, "paidOrders": 12, "previousAmount": 900000.00, "growthPercent": 33.33},
    "yearly":  {"label": "Năm hiện tại", "from": "2026-01-01T00:00:00", "to": "2026-10-28T17:10:00", "amount": 8900000.00, "paidOrders": 89, "previousAmount": 0.00, "growthPercent": null},
    "period":  {"label": "Trong khoảng đang xem", "from": "2026-09-28T17:10:00", "to": "2026-10-28T17:10:00", "amount": 1200000.00, "paidOrders": 12, "previousAmount": 900000.00, "growthPercent": 33.33}
  },
  "revenueTrend": [
    {"bucket": "2026-10-21", "label": "21/10", "amount": 0.00, "paidOrders": 0},
    {"bucket": "2026-10-22", "label": "22/10", "amount": 99000.00, "paidOrders": 1}
  ],
  "trendGranularity": "DAY",
  "subscriptions": {
    "activeMonthly": 14,
    "activeYearly": 3,
    "totalActive": 17,
    "expiringIn30Days": 9,
    "payingUsers": 16
  }
}
```
* **Ghi chú:** `unit` cho biết đơn vị đếm của từng bước (`visitor` / `user` / `order`), UI cần hiển thị chú thích tương ứng. `channels` dùng mô hình **first-touch**: đơn hàng được quy cho kênh UTM mà người dùng nhìn thấy đầu tiên.
* **Bảng KPI doanh thu** (`revenue`) luôn tính theo `paid_at` của đơn `PAID` với cửa sổ cố định (7 ngày / 30 ngày / từ 01/01 năm nay), **không** phụ thuộc tham số `days`; chỉ có `period` là bám theo cửa sổ request. `growthPercent` so với kỳ liền trước có cùng độ dài, và trả `null` khi kỳ trước = 0đ (UI nên hiển thị "—").
* **Biểu đồ dòng tiền** (`revenueTrend`): `trendGranularity = "DAY"` khi khoảng xem ≤ 62 ngày (mỗi điểm một ngày, `bucket` dạng `2026-10-21`), ngược lại `"MONTH"` (`bucket` dạng `2026-09`). Các bucket trống vẫn được trả về với `amount: 0` để vẽ liên tục.
* **Gói tháng / gói năm** (`subscriptions`): phân loại theo `durationDays` của gói (≥ 300 ngày = gói năm), chỉ đếm subscription `status = ACTIVE`. `expiringIn30Days` là số gói sắp hết hạn trong 30 ngày tới, `payingUsers` là số user đã thanh toán tạo ra các gói đang hiệu lực đó.
* **Không có số liệu voucher/giảm giá:** hệ thống hiện không có bảng hay cột `discount` nào (xem lại mục 9 trong bảng chức năng), nên backend không thể thống kê. Nếu cần, phải làm tính năng voucher trước.
