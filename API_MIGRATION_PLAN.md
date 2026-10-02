# Kế hoạch tổng hợp và chuyển đổi từng API

Tài liệu này là backlog chính thức để chuyển backend `../BE` sang Spring Boot theo lát cắt dọc. Mỗi API chỉ được đánh dấu hoàn thành khi đã đi đủ 5 tầng `Entity → DTO → Repository → Service → Controller` và đạt kiểm thử hợp đồng.

## 1. Tổng hợp API cần chuyển

Nguồn kiểm kê: 7 controller .NET hiện tại, tổng cộng **66 HTTP API**.

| Nhóm | Phạm vi | Tổng API | Đã xong | Còn lại |
|---|---|---:|---:|---:|
| AD | Auth, user search, permission, employee/account | 24 | 24 | 0 |
| MD | Organization, title, HR summary | 10 | 10 | 0 |
| MT | Guest session, meeting, participant, lifecycle, message, task | 28 | 28 | 0 |
| CF | Recording, tài liệu cuộc họp, danh sách và download file | 4 | 4 | 0 |
| **Tổng** |  | **66** | **66** | **0** |

Ngoài HTTP API còn có realtime endpoint `/meetinghub` với 3 client command (`JoinMeeting`, `LeaveMeeting`, `SendMessage`) và các event `ParticipantJoined`, `ParticipantLeft`, `PresenceChanged`, `MeetingStatusChanged`, `ReceiveMessage`, `TaskChanged`, `FilesChanged`. Realtime được triển khai qua abstraction `MeetingEventPublisher`; adapter SignalR/WebSocket được chốt trước nhóm MT lifecycle.

## 2. Thứ tự triển khai tổng thể

| Wave | Nhóm API | Lý do |
|---:|---|---|
| 0 | Common + `AD/Auth/Login` | Đã hoàn thành; tạo envelope, JWT, Security và CORS nền tảng |
| 1 | AD/Auth session và profile | **Đã hoàn thành module**; frontend có đủ phiên, hồ sơ và password flow |
| 2 | AD/User + Permission catalog/effective permission | **Đã hoàn thành module**; làm nền cho phân quyền các API quản trị |
| 3 | MD/Organization + MD/Title + Summary | **Đã hoàn thành module**; Employee và Meeting phụ thuộc dữ liệu danh mục này |
| 4 | AD/Employee/Account + permission assignment | **Đã hoàn thành module**; phụ thuộc MD và permission foundation |
| 5 | MT/Guest session & Meeting core (10 APIs) | **Đã hoàn thành module**; tạo aggregate meeting ổn định |
| 6 | MT/Participant, Join, Lifecycle & Message (9 APIs) | **Đã hoàn thành module**; quản lý thành viên, join, lifecycle và chat |
| 7 | MT/Task (9 APIs) | **Đã hoàn thành module**; quản lý công việc (Work/SubWork/Task 3 cấp) |
| 8 | CF/File (4 APIs) | **Đã hoàn thành module**; phụ thuộc meeting membership, MinIO và realtime publisher |
| 9 | Shadow traffic và cutover | So sánh Java/.NET, chuyển lưu lượng theo nhóm và giữ rollback |

Trong mỗi wave vẫn chuyển **từng API một** theo thứ tự ID bên dưới; không tạo hàng loạt controller trước khi Service/Repository tương ứng hoàn tất.

## 3. Definition of Done cho một API

1. **Entity:** mapping đủ field/relationship/index cần dùng; không cho Hibernate tự sửa schema.
2. **DTO:** request/response riêng, Jakarta Validation, enum và JSON field tương thích frontend.
3. **Repository:** truy vấn tối thiểu, không chứa nghiệp vụ; có test truy vấn khi query phức tạp.
4. **Service:** `@Transactional` đúng ranh giới, kiểm tra quyền/nghiệp vụ, audit và side effect rõ ràng.
5. **Controller:** giữ route, method, status code, auth/permission và response shape của .NET.
6. **Test:** unit test Service, controller contract test, repository integration test nếu truy cập DB.
7. **Gate:** không có secret trong source; logging không lộ password/token; API được ghi trạng thái trong bảng tổng hợp.

Lưu ý: controller .NET hiện trả response không đồng nhất (`ApiResponse`, object thuần và `204 No Content`). Giai đoạn migration giữ hợp đồng mà frontend đang dùng; việc chuẩn hóa response là thay đổi API riêng, không gộp vào migration.

## 4. Kế hoạch chi tiết nhóm AD

### 4.1 Auth

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| AD-AUTH-01 `POST /api/Auth/Login` | `AdAccount` | `LoginRequest`, `AuthResultResponse` | Tìm username/email | BCrypt, cập nhật login time, access/refresh JWT | Public; 200/400 đúng envelope; **DONE** |
| AD-AUTH-02 `POST /api/Auth/Refresh` | `AdAccount` | `RefreshTokenRequest`, `AuthResultResponse` | Tìm account theo username trong token | Xác minh issuer/audience/signature/expiry/`Purpose=refresh`, account active; rotate token | Public; 200/400; **DONE** |
| AD-AUTH-03 `GET /api/Auth/Profile` | `AdAccount`, `MdOrganize`, `MdTitle` | `UserProfileResponse` | Projection account + organization/title | Chỉ trả account active; không lộ password/permission JSON | Authenticated; 200/401/404; **DONE** |
| AD-AUTH-04 `PUT /api/Auth/Profile` | `AdAccount` | `UpdateProfileRequest`, `UserProfileResponse` | Tìm account, kiểm tra email trùng | Chuẩn hóa input, cập nhật audit fields trong transaction | Authenticated; 200/400/401/404; **DONE** |
| AD-AUTH-05 `POST /api/Auth/Logout` | `AdAccount` | `LogoutRequest` | Tìm account theo principal | Nếu logout mọi thiết bị thì tăng `TokenVersion`; logout local không ghi DB | Authenticated; 200/400/401; **DONE** |
| AD-AUTH-06 `POST /api/Auth/ChangePassword` | `AdAccount` | `ChangePasswordRequest` | Tìm account theo principal | Verify BCrypt cũ, policy mật khẩu mới, hash mới, tăng `TokenVersion` | Authenticated; 200/400/401; **DONE** |
| AD-AUTH-07 `POST /api/Auth/ForgotPassword` | `AdAccount` | `ForgotPasswordRequest` | Tìm account theo email | Response chống dò email; reset token 15 phút; gọi `PasswordResetNotifier` | Public; mặc định không trả token; **DONE, cần cắm adapter email khi triển khai production** |
| AD-AUTH-08 `POST /api/Auth/ResetPassword` | `AdAccount` | `ResetPasswordRequest` | Tìm account từ reset token | Verify `Purpose=reset`, version chống reuse, đổi hash và thu hồi phiên cũ | Public; 200/400; **DONE** |
| AD-AUTH-09 `POST /api/Auth/Register` | `AdAccount`, `MdOrganize`, `MdTitle` | `RegisterRequest`, `AuthResultResponse` | Kiểm tra username/email; lấy default org/title | Feature flag; hash password; transaction tạo account | Mặc định 404; chặn `ADMIN` làm title mặc định; **DONE** |

### 4.2 User và permission nền tảng

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| AD-USER-01 `GET /api/User/Search?q=&take=` | `AdAccount` | `UserSearchResponse` | Search username/fullName/email, loại current user, sort và limit 1–20 | Chuẩn hóa query; query rỗng trả danh sách rỗng | Authenticated; payload mảng thuần; **DONE** |
| AD-PERM-01 `GET /api/permissions/catalog` | Không cần entity | `PermissionCatalogGroupResponse` | Không cần DB | Build catalog từ constant versioned | Authenticated; payload mảng thuần; **DONE** |
| AD-PERM-02 `GET /api/permissions/me` | `AdAccount`, `MdOrganize`, `MdTitle` | `EffectivePermissionResponse` | Lấy permission JSON ở account/org/title | Merge precedence và validate schema version; malformed JSON fail-closed | Authenticated; payload thuần; **DONE, toàn bộ 48 test đạt** |

### 4.3 Employee/account management

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| AD-EMP-01 `GET /api/human-resources/employees` | `AdAccount`, MD refs | `EmployeeFilter`, `PagedEmployeeResponse` | Specification + Pageable theo org/title/active/keyword | Clamp page/pageSize, projection không lộ password | `HrAccountView`; 200; test filter kết hợp và paging; **DONE** |
| AD-EMP-02 `GET /api/human-resources/employees/{userName}` | `AdAccount`, MD refs | `EmployeeDetailResponse` | Detail projection | Kiểm tra tồn tại, map effective metadata | `HrAccountView`; 200/404; **DONE** |
| AD-EMP-03 `POST /api/human-resources/employees` | `AdAccount`, MD refs | `EmployeeRequest`, `CreatedEmployeeResponse` | Unique username/email; tồn tại org/title | Sinh temporary password, BCrypt, `MustChangePassword=true`, transaction | `HrAccountCreate`; 200/400/409; không log password; **DONE** |
| AD-EMP-04 `POST /api/human-resources/directory-employees` | `AdAccount`, MD refs | `CreateDirectoryEmployeeRequest`, `CreatedDirectoryEmployeeResponse` | Unique email; generate username chống trùng | Chuẩn hóa tên, sinh username/password theo rule hiện tại | `HrAccountCreate`; 200/400/409; test Unicode và collision username; **DONE** |
| AD-EMP-05 `PUT /api/human-resources/employees/{userName}` | `AdAccount`, MD refs | `UpdateEmployeeRequest` | Tìm account, unique email, refs tồn tại | Cập nhật profile/org/title và audit fields | `HrAccountUpdate`; 204/400/404/409; **DONE** |
| AD-EMP-06 `PUT /api/human-resources/employees/{userName}/organization` | `AdAccount`, `MdOrganize` | `TransferEmployeeRequest` | Tìm account/org | Chuyển đơn vị, xử lý effective date theo quyết định nghiệp vụ, audit reason | `HrAccountTransfer`; 204/400/404; **DONE** |
| AD-EMP-07 `PUT /api/human-resources/employees/{userName}/title` | `AdAccount`, `MdTitle` | `ChangeTitleRequest` | Tìm account/title | Đổi chức danh và audit | `HrAccountChangeTitle`; 204/400/404; **DONE** |
| AD-EMP-08 `PUT /api/human-resources/employees/{userName}/status` | `AdAccount` | `ChangeStatusRequest` | Tìm account | Lock/unlock; tăng `TokenVersion` khi khóa để revoke phiên | `HrAccountLock`; 204/400/404; test token cũ bị từ chối; **DONE** |
| AD-EMP-09 `POST /api/human-resources/employees/{userName}/reset-password` | `AdAccount` | `ResetEmployeePasswordResponse` | Tìm account | Sinh/hash mật khẩu tạm, `MustChangePassword=true`, tăng token version | `HrAccountResetPassword`; 200/404; chỉ trả password tạm cho caller được phép và không log; **DONE** |
| AD-EMP-10 `GET /api/human-resources/{type}/{id}/permissions` | `AdAccount`, `MdOrganize`, `MdTitle` | `PermissionDocumentResponse` | Chọn entity theo allow-list type | Đọc/parse JSON; từ chối type không hợp lệ | `HrView`; 200/400/404; test account/org/title; **DONE** |
| AD-EMP-11 `PUT /api/human-resources/{type}/{id}/permissions` | Cùng trên | `PermissionUpdateRequest` | Tìm target theo allow-list | Validate version/code/effect, serialize JSON, audit | `HrAccountPermission`; 204/400/404; **DONE** |
| AD-EMP-12 `GET /api/human-resources/employees/{userName}/effective-permissions` | Cùng trên | `EffectivePermissionResponse` | Load ba cấp permission | Tái sử dụng permission merge service, không nhân đôi rule | `HrAccountView`; 200/404; kết quả khớp `permissions/me` khi cùng user; **DONE** |

## 5. Kế hoạch chi tiết nhóm MD

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| MD-SUM-01 `GET /api/human-resources/summary` | `AdAccount`, `MdOrganize`, `MdTitle` | `HumanResourceSummaryResponse` | Count/projection tối ưu | Tổng hợp active/inactive/org/title | `HrView`; 200; **DONE** |
| MD-ORG-01 `GET /api/human-resources/organization-tree` | `MdOrganize` | `OrganizationNodeResponse` | Lấy active/all theo contract, sort order | Dựng cây, phát hiện orphan/cycle | `HrView`; 200; **DONE** |
| MD-ORG-02 `POST /api/human-resources/organizations` | `MdOrganize` | `OrganizationRequest`, `OrganizationResponse` | Unique id/name rule và parent lookup | Tạo node, validate parent, audit | `HrOrgCreate`; 200/400/409; **DONE** |
| MD-ORG-03 `PUT /api/human-resources/organizations/{id}` | `MdOrganize` | `OrganizationRequest` | Tìm node/parent | Cập nhật nhưng không tạo cycle; audit | `HrOrgUpdate`; 204/400/404/409; **DONE** |
| MD-ORG-04 `PUT /api/human-resources/organizations/{id}/move` | `MdOrganize` | `MoveOrganizationRequest` | Load node, parent và descendants | Move/reorder transaction; cấm self/descendant parent | `HrOrgMove`; 204/400/404/409; **DONE** |
| MD-ORG-05 `DELETE /api/human-resources/organizations/{id}` | `MdOrganize`, `AdAccount` | Không body | Kiểm tra child và account tham chiếu | Chỉ xóa khi không có phụ thuộc hoặc áp dụng policy inactive đã chốt | `HrOrgDelete`; 204/404/409; **DONE** |
| MD-TITLE-01 `GET /api/human-resources/titles` | `MdTitle` | `TitleResponse` | Sort `OrderNumber`, `Name` | Projection danh mục | `HrTitleView`; 200; **DONE** |
| MD-TITLE-02 `POST /api/human-resources/titles` | `MdTitle` | `TitleRequest`, `TitleResponse` | Unique code | Normalize code, tạo và audit | `HrTitleCreate`; 200/400/409; **DONE** |
| MD-TITLE-03 `PUT /api/human-resources/titles/{code}` | `MdTitle` | `TitleRequest` | Tìm title | Không đổi PK ngoài contract; cập nhật và audit | `HrTitleUpdate`; 204/400/404; **DONE** |
| MD-TITLE-04 `DELETE /api/human-resources/titles/{code}` | `MdTitle`, `AdAccount` | Không body | Kiểm tra account tham chiếu | Chỉ xóa khi không có phụ thuộc hoặc chuyển inactive theo policy | `HrTitleDelete`; 204/404/409; **DONE** |

## 6. Kế hoạch chi tiết nhóm MT

### 6.1 Guest và meeting core

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| MT-GUEST-01 `POST /api/Auth/GuestSession` | `MeetingInfo`, `MeetingPersonal` | `GuestSessionRequest`, `AuthResultResponse` | Tìm meeting/guest membership | Kiểm tra meeting/settings/status, tạo participant guest và JWT 4 giờ | Public; 200/400; **DONE** |
| MT-MEET-01 `GET /api/Meeting/dashboard` | Meeting + participant | `MeetingDashboardResponse` | Query upcoming/ongoing/history theo membership | Tính counters và next meetings | Authenticated; 200; **DONE** |
| MT-MEET-02 `POST /api/Meeting/query` | Meeting + participant | `MeetingSearchRequest`, `PagedResultResponse` | Specification tab/keyword/status/org/date + Pageable | Áp visibility, sort và paging | Authenticated; 200; **DONE** |
| MT-MEET-03 `GET /api/Meeting/{meetingId}` | Meeting, participant, audit | `MeetingDetailResponse` | Fetch aggregate tránh N+1 | Kiểm tra quyền xem, mask password hash, map rowVersion | Authenticated; 200/403/404; **DONE** |
| MT-MEET-04 `POST /api/Meeting` | Meeting, participant, audit | `CreateMeetingRequest`, `MeetingDetailResponse` | Unique room code; account lookup | Validate time/settings/participants, tạo host, audit trong transaction | Authenticated; 200/400/409; **DONE** |
| MT-MEET-05 `PATCH /api/Meeting/{meetingId}` | Meeting, audit | `UpdateMeetingRequest`, detail response | Load meeting theo id | Chỉ người quản lý; validate state; optimistic concurrency bằng `RowVersion`; audit | Authenticated; 200/400/403/404/409; **DONE** |
| MT-MEET-06 `DELETE /api/Meeting/{meetingId}` | Meeting aggregate | Không body | Load meeting + dependencies | Chỉ xóa draft; ưu tiên soft-delete; audit | Authenticated; 200/403/404/409; **DONE** |
| MT-MEET-07 `POST /api/Meeting/{meetingId}/cancel` | Meeting, audit | `CancelMeetingRequest` | Load meeting | State transition sang cancelled, reason bắt buộc, audit | Authenticated; 200/400/403/404/409; **DONE** |
| MT-MEET-08 `POST /api/Meeting/{meetingId}/archive` | Meeting, audit | Không body | Load meeting | Chỉ archive trạng thái hợp lệ; audit | Authenticated; 200/403/404/409; **DONE** |
| MT-MEET-09 `POST /api/Meeting/quick` | Meeting, participant, audit | `QuickMeetingRequest`, detail response | Account lookup, room-code uniqueness | Tạo meeting ongoing/quick theo rule hiện tại, host + participant, audit | Authenticated; 200/400/409; **DONE** |

### 6.2 Participant, join và lifecycle

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| MT-PART-01 `POST /api/Meeting/{meetingId}/participants` | Meeting, participant, account | `UpdateMeetingParticipantsRequest`, detail response | Lookup users và existing membership | Manager-only, deduplicate, validate roles, transaction + audit | Authenticated; 200/400/403/404/409; **DONE** |
| MT-PART-02 `DELETE /api/Meeting/{meetingId}/participants/{userName}` | Meeting, participant | Không body | Tìm membership | Manager-only; cấm xóa host cuối; audit | Authenticated; 200/403/404/409; **DONE** |
| MT-JOIN-01 `GET /api/Meeting/{meetingId}/join-info` | Meeting, participant | `MeetingJoinInfoResponse` | Load meeting + membership | Kiểm tra state/quyền; map Jitsi config; không lộ secret | Authenticated; 200/403/404/409; **DONE** |
| MT-LIFE-01 `POST /api/Meeting/{meetingId}/start` | Meeting, audit | Không body | Lock/load meeting | State transition scheduled→ongoing, host permission, audit | Authenticated; 200/403/404/409; **DONE** |
| MT-LIFE-02 `POST /api/Meeting/{meetingId}/end` | Meeting, participant, audit | Không body | Lock/load aggregate | ongoing→ended, close presence, audit | Authenticated; 200/403/404/409; **DONE** |
| MT-LIFE-03 `POST /api/Meeting/{meetingId}/join` | Meeting, participant | Không body | Tìm membership | Validate state, set joined/time | Authenticated; 200/403/404/409; **DONE** |
| MT-LIFE-04 `POST /api/Meeting/{meetingId}/leave` | Participant | Không body | Tìm membership | Set left state | Authenticated; 200/403/404; **DONE** |

### 6.3 Message

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| MT-MSG-01 `GET /api/Meeting/{meetingId}/messages` | `MeetingMessage`, participant | `MeetingMessageResponse` | Query theo meeting, sort thời gian | Chỉ member; map sender metadata | Authenticated; 200/403/404; **DONE** |
| MT-MSG-02 `POST /api/Meeting/{meetingId}/messages` | `MeetingMessage`, participant | `SendMeetingMessageRequest`, response | Save message | Chỉ member, validate nội dung/độ dài | Authenticated; 200/400/403/404; **DONE** |

### 6.4 Task

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| MT-TASK-01 `POST /api/Task/query` | Task, share, meeting, account | `TaskSearchRequest`, `TaskSearchResultResponse` | Specification shortcut/filter/tree + paging | Tính quyền xem, summary, progress và overdue/today | Authenticated; test shortcut/filter/time-zone/paging; **DONE** |
| MT-TASK-02 `GET /api/Task/{taskId}` | Task, children, shares | `TaskDetailResponse` | Fetch detail có kiểm soát N+1 | Kiểm tra creator/share/public/meeting membership | Authenticated; 200/403/404; **DONE** |
| MT-TASK-03 `POST /api/Task` | Task, meeting | `CreateTaskRequest`, list item response | Parent/meeting/account lookup | Validate level/parent/assignee, tạo audit fields | Authenticated; 200/400/403/404/409; **DONE** |
| MT-TASK-04 `PUT /api/Task/{taskId}` | Task hierarchy | `UpdateTaskRequest`, list item response | Load task/parent | Quyền edit; chống cycle; validate level | Authenticated; 200/400/403/404/409; **DONE** |
| MT-TASK-05 `PATCH /api/Task/{taskId}/status` | Task hierarchy | `UpdateTaskStatusRequest`, response | Load task | Quyền edit; state enum; cập nhật status | Authenticated; 200/400/403/404; **DONE** |
| MT-TASK-06 `DELETE /api/Task/{taskId}` | Task, child, share | Không body/list item event payload | Load aggregate | Creator-only; policy child restrict | Authenticated; 200/403/404/409; **DONE** |
| MT-TASK-07 `PUT /api/Task/{taskId}/visibility` | Task | `TaskVisibilityRequest` | Load task | Creator-only; cập nhật public flag | Authenticated; 200/403/404; **DONE** |
| MT-TASK-08 `POST /api/Task/{taskId}/shares` | Task, share, account | `TaskShareInputRequest`, detail response | Unique task/user; account lookup | Creator-only; upsert permission | Authenticated; 200/400/403/404/409; **DONE** |
| MT-TASK-09 `DELETE /api/Task/{taskId}/shares/{userName}` | Task, share | Không body | Tìm share | Creator-only; xóa share | Authenticated; 200/403/404; **DONE** |

## 7. Kế hoạch chi tiết nhóm CF

| ID / API | Entity | DTO | Repository | Service | Controller và kiểm thử chấp nhận |
|---|---|---|---|---|---|
| CF-FILE-01 `POST /api/File/UploadFilesRecord` | Meeting, `CmFile` | Multipart `meetingId`, files; `UploadedFileResponse` | Tìm meeting, lưu metadata | Validate extension/size, stream MinIO, rollback object khi DB lỗi | Hiện anonymous, tối đa 2GB. **Gate bảo mật:** yêu cầu Jibri service credential/HMAC hoặc network allow-list trước production; test streaming và cleanup; **DONE** |
| CF-FILE-02 `POST /api/File/Meetings/{meetingId}/files` | Meeting, participant, `CmFile` | Multipart file; response | Membership + save metadata | Validate member/type/200MB, stream MinIO, transaction/compensation, publish `FilesChanged` | Authenticated; 200/400/403/404; không buffer toàn file vào RAM; **DONE** |
| CF-FILE-03 `GET /api/File/GetMeetingFiles/{meetingId}` | Meeting, participant, `CmFile` | `MeetingFileResponse` | Query reference id, sort create date | Chỉ member; projection metadata | Authenticated; giữ payload mảng thuần; 200/403/404; **DONE** |
| CF-FILE-04 `GET /api/File/Download/{fileId}` | `CmFile`, meeting, participant | `FileDownloadResponse` | Resolve file→meeting→membership | Chỉ member; tạo presigned URL 900 giây; audit tùy yêu cầu | Authenticated; 200/403/404; test URL expiry và object missing; **DONE** |

## 8. Kế hoạch realtime `/meetinghub`

Realtime không được nhúng trực tiếp vào Service. Service phát domain event sau commit; adapter realtime chuyển event thành contract frontend.

| Realtime API/event | Kế hoạch |
|---|---|
| Client `JoinMeeting(meetingId)` | JWT auth; kiểm tra meeting còn hoạt động và membership; subscribe room; phát `ParticipantJoined` + `PresenceChanged` |
| Client `LeaveMeeting(meetingId)` | Unsubscribe room; phát `ParticipantLeft` + `PresenceChanged` |
| Client `SendMessage(meetingId,envelope)` | Kiểm tra membership; chỉ relay nếu contract cũ vẫn cần, ưu tiên HTTP API lưu message trước |
| Server status/presence/message/task/file events | Chuẩn hóa schema event, thêm `eventId`, `occurredAt`, giữ field cũ để FE không đổi |
| Transport | Chốt một trong hai: giữ SignalR-compatible gateway hoặc chuyển FE sang Spring WebSocket/STOMP; phải có thử nghiệm reconnect, auth query token và duplicate delivery |

## 9. Cổng quyết định trước khi code

1. **Public Register:** chọn default organization/title/role an toàn; tuyệt đối không cấp `ADMIN` mặc định.
2. **Forgot Password:** production gửi token qua email; không trả token trong response/log.
3. **Recording upload:** xác thực Jibri service-to-service trước khi giữ route anonymous.
4. **Realtime:** chọn gateway tương thích SignalR hay đổi frontend sang STOMP trước wave 6.
5. **RowVersion:** chốt cách optimistic concurrency SQL Server trong JPA trước `MT-MEET-05`.
6. **Delete policy:** chốt hard-delete/soft-delete cho organization, title, meeting và task.
7. **Time:** chốt lưu UTC cho `datetime2`, convert `Asia/Bangkok` tại DTO/UI.

## 10. Theo dõi trạng thái

Mỗi lần hoàn thành một API:

- đổi trạng thái API trong tài liệu này thành `DONE`;
- ghi số test và lệnh xác minh;
- cập nhật `README.md`;
- không bắt đầu API kế tiếp nếu API hiện tại còn lỗi build, contract hoặc migration DB.
