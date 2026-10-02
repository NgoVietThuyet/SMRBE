# Kế hoạch chuyển backend .NET sang Spring Boot

Kế hoạch tổng hợp 66 HTTP API và chi tiết chuyển đổi 5 tầng cho từng route được quản lý tại [`API_MIGRATION_PLAN.md`](API_MIGRATION_PLAN.md).

## Nguyên tắc

- Backend .NET trong `../BE` là nguồn đối chiếu cho tới khi hoàn tất nghiệm thu.
- Backend Java nằm riêng trong `BE-Java`; không sửa hoặc xóa mã C# trong quá trình chuyển đổi.
- Chuyển theo từng module để tăng tốc, nhưng mọi API trong module vẫn phải đi đủ `Entity → DTO → Repository → Service → Controller` và đạt test trước khi chuyển module tiếp theo.
- Giữ nguyên schema SQL Server và hợp đồng HTTP hiện tại trong giai đoạn đầu.
- Java không tự động cập nhật schema. Mọi thay đổi database sau này phải có migration được rà soát riêng.
- Bí mật kết nối, JWT và MinIO chỉ nhận từ biến môi trường hoặc secret store.

## Quy trình áp dụng cho mỗi module

1. Kiểm kê toàn bộ API thuộc module và dependency dùng chung.
2. **Entity:** dùng hoặc bổ sung entity/relationship đúng schema hiện hữu.
3. **DTO:** định nghĩa request/response, validation và hợp đồng JSON cho các API của module.
4. **Repository:** thêm các truy vấn cần cho module.
5. **Service:** chuyển nghiệp vụ, transaction, bảo mật và side effect.
6. **Controller:** giữ route, HTTP method, status code, envelope và phân quyền của .NET.
7. **Nghiệm thu:** unit test, contract test và integration test database trước khi chuyển module.

Không tạo hàng loạt DTO, Repository, Service hoặc Controller ngoài module đang được chọn.

## Thứ tự nhóm API

### 0. Đóng băng đường chuẩn

- Lập danh mục endpoint, mã trạng thái, request/response và quyền truy cập của backend .NET.
- Chụp schema từ EF Core migrations và chuẩn bị dữ liệu kiểm thử đại diện.
- Ghi nhận các tích hợp cần thay thế: JWT, SignalR, MinIO, Swagger/OpenAPI và CORS.

**Cổng nghiệm thu:** backend .NET build được và có bộ mẫu phản hồi API để so sánh.

### 1. Nền tảng Entity - đã triển khai mã nguồn

- Ánh xạ 10 entity sang Jakarta Persistence.
- Giữ nguyên tên bảng, tên cột, độ dài khóa, index, unique constraint và quan hệ.
- Dùng `LocalDateTime` cho SQL Server `datetime2`, `BigDecimal` cho `decimal(18,2)`.
- Chỉ cho phép Hibernate `validate` schema; không `create`, `update` hoặc `create-drop`.
- Giữ `RowVersion` SQL Server ở chế độ database-generated/read-only. Quy tắc kiểm tra xung đột cập nhật sẽ được hoàn thiện ở tầng Service.

**Cổng nghiệm thu:** biên dịch thành công, kiểm thử annotation thành công, và schema validation thành công trên database thử nghiệm.

### 2. Nhóm AD

1. `AD/Auth` — **đã hoàn thành 9 HTTP API, đủ 5 tầng và contract test**.
2. `AD/Auth/ForgotPassword` — cần cắm adapter email production vào `PasswordResetNotifier`.
3. `AD/User/Search` và `AD/Permissions` — **đã hoàn thành 3 HTTP API**.
4. `AD/Employee/Account` — module AD tiếp theo sau khi hoàn thành MD foundation.

`GuestSession` được xếp vào nhóm MT vì phụ thuộc trạng thái cuộc họp và người tham gia.

### 3. Nhóm MD

1. `MD/Organization`.
2. `MD/Title`.
3. Các API danh mục và phân quyền liên quan.

### 4. Nhóm MT

1. `MT/Meeting` — danh sách, chi tiết, tạo, cập nhật, trạng thái.
2. `MT/Participant` và `GuestSession`.
3. `MT/Task` và chia sẻ công việc.
4. `MT/Message`, audit log và realtime.

Quyết định thay SignalR bằng WebSocket/STOMP phải được chốt trước lát cắt realtime.

### 5. Nhóm CF

1. `CF/File` metadata.
2. Upload/download MinIO.
3. Liên kết file với meeting và biên bản.

### 6. Chuyển lưu lượng và gỡ backend cũ

- Chạy song song/shadow traffic trên môi trường kiểm thử.
- So sánh response, log lỗi, thời gian phản hồi và thay đổi dữ liệu.
- Chuyển lưu lượng theo module, có phương án quay lại .NET.
- Chỉ gỡ backend .NET sau thời gian ổn định và có bản sao lưu database.

## Rủi ro cần theo dõi

- `RowVersion` của SQL Server không tương đương trực tiếp với `@Version` số của JPA.
- EF Core và Hibernate có quy tắc cascade/lazy loading khác nhau.
- `datetime2` hiện không chứa offset; toàn hệ thống phải thống nhất UTC hay `Asia/Bangkok` ở tầng Service/DTO.
- Các tên cột lịch sử `RefrenceFileId` và `Extention` phải được giữ để tương thích schema.
- SignalR không có thay thế Java tương thích giao thức một-một nếu frontend đang dùng client SignalR.
