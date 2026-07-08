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
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ...",
      "email": "nguyenvana@example.com"
    }
    ```
  * **400 Bad Request (Sai thông tin đăng nhập):**
    ```text
    Sai tài khoản hoặc mật khẩu!
    ```

#### 3.1.3. Đăng xuất hệ thống (Logout)
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
