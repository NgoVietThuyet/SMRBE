# SMR Backend - Spring Boot

Backend Java được chuyển đổi theo từng module từ `../BE` và dùng chung schema SQL Server hiện tại. Mọi API trong module đều hoàn thành trọn lát cắt dọc `Entity → DTO → Repository → Service → Controller` trước khi chuyển module tiếp theo.

Danh mục 66 API, thứ tự wave và kế hoạch 5 tầng cho từng route nằm trong [`API_MIGRATION_PLAN.md`](API_MIGRATION_PLAN.md).

## Trạng thái chuyển đổi

| Nhóm/API | Entity | DTO | Repository | Service | Controller | Trạng thái |
|---|---:|---:|---:|---:|---:|---|
| Common entity mapping | ✓ | — | — | — | — | Chờ validate trên database thử nghiệm |
| AD/Auth (9 API, không gồm GuestSession) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành |
| AD/User & Permission (3 API) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành; tổng bộ test 48 |
| MD/Master data (10 API) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành; tổng bộ test 73 |
| AD/Employee & Permission Assignment (12 API) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành; tổng bộ test 101 |
| MT/Guest & Meeting Core (10 API) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành Wave 5; tổng bộ test 123 |
| MT/Participant, Join, Lifecycle & Message (9 API) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành Wave 6; tổng bộ test 141 |
| MT/Task (9 API) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành Wave 7; tổng bộ test 160 |
| CF/File (4 API) | ✓ | ✓ | ✓ | ✓ | ✓ | Hoàn thành Wave 8; tổng bộ test 174 |

Không chạy đồng thời hai backend ở chế độ tự động thay đổi schema. Trong giai đoạn chuyển tiếp, Java chỉ xác thực schema bằng `ddl-auto=validate` và không tạo hoặc sửa bảng.

## Yêu cầu

- JDK 21 trở lên
- Không bắt buộc cài Maven riêng; dự án có Maven Wrapper
- SQL Server chứa schema do backend .NET hiện tại quản lý

## Cấu hình môi trường

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=KLTNSMR;encrypt=true;trustServerCertificate=true'
$env:DB_USERNAME = 'sa'
$env:DB_PASSWORD = '<password>'
$env:JWT_KEY = '<ít nhất 32 byte>'
$env:JWT_ISSUER = 'SMR.Api'
$env:JWT_AUDIENCE = 'SMR.Web'
$env:PUBLIC_REGISTRATION = 'false'
$env:REGISTRATION_ORG_ID = '<org mặc định, bắt buộc khi bật đăng ký>'
$env:REGISTRATION_TITLE_CODE = '<title mặc định không phải ADMIN>'
$env:EXPOSE_RESET_TOKEN = 'false'
```

Không lưu mật khẩu database vào Git.

## Kiểm tra

```powershell
.\mvnw.cmd test
```

Module Auth hiện có 9 API: Register, Login, Refresh, Profile GET/PUT, ChangePassword, Logout, ForgotPassword và ResetPassword. `GuestSession` được triển khai cùng module MT vì phụ thuộc meeting.

Module Employee/Account & Permission Assignment hiện có 12 API: search/detail nhân sự, tạo nhân sự trực tiếp/directory (tự chuẩn hóa username & password tạm), cập nhật nhân sự, điều chuyển phòng ban, đổi chức danh, khóa/mở tài khoản (thu hồi tokenVersion), reset password, xem/gán permission theo entity và get effective permissions.

Module MT/Participant, Join, Lifecycle & Message hiện có 9 API: thêm thành viên (`POST /{id}/participants`), xóa thành viên (`DELETE /{id}/participants/{user}`), lấy thông tin join Jitsi (`GET /{id}/join-info`), chuyển trạng thái bắt đầu (`POST /{id}/start`), kết thúc (`POST /{id}/end`), tham gia (`POST /{id}/join`), rời cuộc họp (`POST /{id}/leave`), lấy danh sách tin nhắn (`GET /{id}/messages`), gửi tin nhắn (`POST /{id}/messages`).

Module MT/Task hiện có 9 API: truy vấn công việc phân trang & số đếm summary (`POST /api/Task/query`), xem chi tiết công việc (`GET /api/Task/{id}`), tạo công việc (`POST /api/Task`), cập nhật công việc & chuyển cây con (`PUT /api/Task/{id}`), đổi trạng thái (`PATCH /api/Task/{id}/status`), xóa công việc (`DELETE /api/Task/{id}`), đổi quyền công khai (`PUT /api/Task/{id}/visibility`), chia sẻ công việc (`POST /api/Task/{id}/shares`), xóa người được chia sẻ (`DELETE /api/Task/{id}/shares/{user}`).

Module CF/File hiện có 4 API: upload file ghi âm cuộc họp Jibri (`POST /api/File/UploadFilesRecord`), upload tài liệu cuộc họp (`POST /api/File/Meetings/{meetingId}/files`), lấy danh sách tệp tin cuộc họp (`GET /api/File/GetMeetingFiles/{meetingId}`), tải xuống tệp tin / lấy presigned URL (`GET /api/File/Download/{fileId}`).

`ForgotPassword` gọi port `PasswordResetNotifier`; môi trường production phải cung cấp adapter email/SMS. Reset token mặc định không được trả trong response; chỉ bật `EXPOSE_RESET_TOKEN=true` cho môi trường legacy/dev có kiểm soát.
