# WORKFLOW PHÁT TRIỂN DỰ ÁN SMART MEETING

> Tài liệu chỉ dẫn bắt buộc dành cho AI Agent và lập trình viên tham gia phát triển hệ thống Smart Meeting.

## 1. Mục đích

File này quy định cách AI Agent tiếp nhận yêu cầu, đọc tài liệu, phân tích, lập kế hoạch, triển khai, kiểm thử và bàn giao mã nguồn. Mục tiêu là bảo đảm mọi thay đổi:

- Đúng nghiệp vụ Smart Meeting.
- Phù hợp kiến trúc hiện có.
- Không phá vỡ chức năng đã chạy.
- Có kiểm tra quyền, bảo mật và dữ liệu.
- Có kiểm thử và bằng chứng xác minh.
- Có thể tiếp tục phát triển bởi Agent hoặc lập trình viên khác.

Mọi AI Agent phải đọc toàn bộ file này trước khi sửa mã nguồn.

---

## 2. Nguyên tắc ưu tiên tài liệu

Khi có thông tin mâu thuẫn, áp dụng thứ tự ưu tiên sau:

1. Yêu cầu mới nhất của chủ dự án trong phiên làm việc hiện tại.
2. Tiêu chí nghiệm thu của task đang thực hiện.
3. File đặc tả chương/module liên quan trong `smart_meeting_tach_chuong/`.
4. File `DAC_TA_CHUC_NANG_HE_THONG_SMART_MEETING.md`.
5. File `WORKFLOW.md` này.
6. Mã nguồn, migration, API contract và test hiện đang chạy.
7. Tài liệu khóa luận/PDF gốc.
8. Giả định của AI Agent.

Nếu hai nguồn cùng mức ưu tiên mâu thuẫn, Agent phải dừng tại điểm cần quyết định, trình bày rõ hai phương án và hỏi chủ dự án. Không được âm thầm chọn một phương án làm thay đổi nghiệp vụ, dữ liệu hoặc kiến trúc.

### 2.1. Quy tắc đối với công nghệ frontend

Tài liệu khóa luận gốc mô tả Angular, nhưng định hướng hiện tại của dự án sử dụng Vue. Trừ khi repository hoặc chủ dự án quy định khác, frontend mới phải dùng:

- Vue 3.
- Composition API và `<script setup>`.
- TypeScript.
- Vue Router.
- Pinia cho trạng thái dùng chung.
- Một lớp API client tập trung; không gọi HTTP rải rác trong component.

Không đưa Angular vào phần code mới chỉ vì tài liệu PDF cũ có nhắc đến Angular.

---

## 3. Bối cảnh và kiến trúc chuẩn

Smart Meeting quản lý toàn bộ vòng đời cuộc họp: trước cuộc họp, trong cuộc họp và sau cuộc họp.

### 3.1. Các thành phần chính

| Thành phần | Công nghệ/định hướng | Trách nhiệm |
| --- | --- | --- |
| Web frontend | Vue 3 + TypeScript | Giao diện, điều hướng, trạng thái client, tích hợp phòng họp |
| Core API | ASP.NET Core .NET 8 | Xác thực, phân quyền, nghiệp vụ, API, điều phối dịch vụ |
| Realtime | SignalR/WebSocket | Thông báo, chat và đồng bộ trạng thái cuộc họp |
| CSDL quan hệ | SQL Server + EF Core | Dữ liệu nghiệp vụ có cấu trúc |
| Object storage | MinIO/S3 | Bản ghi, tài liệu, tệp đính kèm, sản phẩm AI |
| Cache/session | Redis | Cache và trạng thái truy cập nhanh |
| Background jobs | Hangfire hoặc hạ tầng queue hiện có | Tác vụ nền, retry, hậu xử lý |
| Video meeting | Jitsi Meet + Jibri | Âm thanh, hình ảnh, chia sẻ màn hình, ghi hình |
| Tài liệu cộng tác | OnlyOffice | Đồng biên tập tài liệu |
| Bảng trắng | Excalidraw server | Cộng tác bảng trắng thời gian thực |
| AI service | Python + FastAPI | Speech-to-text, diarization, tóm tắt, sinh biên bản |

### 3.2. Nguyên tắc kiến trúc

- Core API là nguồn sự thật cho nghiệp vụ, quyền và trạng thái bền vững.
- Frontend không phải lớp bảo mật; mọi quyền phải được kiểm tra lại ở backend.
- Dịch vụ ngoài không được truy cập dữ liệu tùy ý; chỉ nhận dữ liệu và quyền tối thiểu cần thiết.
- Tác vụ AI hoặc xử lý media dài phải chạy nền, không giữ HTTP request chờ hoàn thành.
- Metadata tệp nằm trong CSDL; nội dung nhị phân nằm trong object storage.
- Mọi tích hợp phải có timeout, log, xử lý lỗi và chiến lược retry phù hợp.

---

## 4. Quy tắc bắt buộc của AI Agent

### 4.1. Trước khi code

Agent phải:

1. Đọc `WORKFLOW.md`.
2. Tìm và đọc hướng dẫn cấp repository như `AGENTS.md`, `README.md`, tài liệu setup và quy ước code.
3. Đọc chương đặc tả liên quan trực tiếp đến task.
4. Khảo sát cấu trúc repository và code tương tự đang tồn tại.
5. Kiểm tra trạng thái Git và bảo toàn thay đổi chưa commit của người dùng.
6. Xác định phạm vi, phụ thuộc, rủi ro và tiêu chí nghiệm thu.
7. Viết kế hoạch ngắn theo các bước có thể kiểm chứng.

Không bắt đầu bằng việc tạo hàng loạt file khi chưa hiểu luồng nghiệp vụ và mẫu kiến trúc hiện có.

### 4.2. Trong khi code

- Thực hiện theo từng lát chức năng nhỏ có thể chạy và kiểm thử.
- Ưu tiên sửa tối thiểu trong đúng phạm vi task.
- Tái sử dụng convention, component, service và utility hiện có.
- Không đổi tên, di chuyển hoặc định dạng hàng loạt file không liên quan.
- Không thêm dependency nếu thư viện hiện có đã đáp ứng được yêu cầu.
- Không hard-code secret, token, URL môi trường, ID người dùng hoặc quyền.
- Không bỏ qua validation, authorization hoặc lỗi chỉ để demo chạy được.
- Không dùng dữ liệu giả trong luồng production nếu không được yêu cầu rõ.
- Không tạo API contract frontend khác với backend.
- Sau mỗi phần có ý nghĩa, chạy kiểm tra phù hợp thay vì chờ đến cuối.

### 4.3. Khi gặp thông tin thiếu

Agent được tự quyết định các chi tiết triển khai nhỏ, dễ đảo ngược và phù hợp convention hiện có. Agent phải hỏi lại nếu thiếu thông tin có thể làm thay đổi:

- Nghiệp vụ hoặc tiêu chí nghiệm thu.
- Schema dữ liệu/migration.
- API public hoặc event contract.
- Cơ chế xác thực/phân quyền.
- Hành vi xóa, ghi đè hoặc mất dữ liệu.
- Công nghệ hoặc kiến trúc chính.
- Chi phí dịch vụ ngoài hoặc cách dùng dữ liệu nhạy cảm.

Khi cần giả định tạm thời, phải ghi rõ giả định trong kế hoạch và báo cáo bàn giao.

---

## 5. Workflow chuẩn cho mỗi task

### Bước 1 - Chuẩn hóa yêu cầu

Viết lại task thành một mô tả ngắn gồm:

- Mục tiêu người dùng.
- Actor thực hiện.
- Điều kiện trước.
- Luồng chính.
- Luồng lỗi/ngoại lệ quan trọng.
- Dữ liệu đầu vào và đầu ra.
- Quyền cần có.
- Tiêu chí hoàn thành.

Nếu task lớn hơn một module hoặc không thể kiểm thử độc lập trong một lượt, chia thành các task nhỏ theo vertical slice.

### Bước 2 - Truy vết đặc tả

Đối chiếu ít nhất các tài liệu sau khi có liên quan:

- Chương 2: tác nhân và phạm vi quyền.
- Chương 3: trạng thái và vòng đời cuộc họp.
- Chương 4: đặc tả chức năng chi tiết.
- Chương 5 và 6: quy trình, quy tắc nghiệp vụ.
- Chương 7: dữ liệu đề xuất.
- Chương 8: yêu cầu phi chức năng.
- Chương 9: danh sách màn hình.
- Chương 10: API và sự kiện.
- Chương 11: tiêu chí nghiệm thu.

Agent phải chỉ rõ phần nào là yêu cầu đã có và phần nào là đề xuất kỹ thuật mới.

### Bước 3 - Khảo sát code hiện tại

Tìm kiếm trước khi tạo mới:

- Route, page và component tương tự.
- Entity, DTO, service, repository và controller liên quan.
- Permission constants và authorization policy.
- API client, interceptor và error handler.
- SignalR hub/event contract.
- Migration và seed data.
- Test fixture, mock và pattern kiểm thử.

Ưu tiên mở rộng pattern đang hoạt động. Nếu pattern hiện tại có lỗi thiết kế, chỉ refactor trong phạm vi cần thiết và phải giải thích tác động.

### Bước 4 - Thiết kế lát chức năng

Trước khi sửa code, xác định chuỗi thay đổi theo thứ tự:

1. Quy tắc nghiệp vụ và quyền.
2. Data model/migration nếu cần.
3. DTO và validation.
4. Service/repository.
5. API endpoint.
6. Realtime/background job nếu có.
7. API client và store frontend.
8. Page/component/UI states.
9. Test.
10. Tài liệu cấu hình hoặc hướng dẫn chạy.

Không phải task nào cũng cần đủ 10 phần; chỉ thực hiện phần có liên quan.

### Bước 5 - Chốt contract trước

Nếu thay đổi liên quan nhiều thành phần, thống nhất contract trước khi code hai phía.

#### REST API

Phải xác định:

- Method và route.
- Request params/body.
- Response DTO.
- Mã lỗi và HTTP status.
- Permission/policy.
- Idempotency và pagination nếu liên quan.

Response lỗi phải có cấu trúc nhất quán và mã lỗi máy có thể đọc; không để frontend phụ thuộc vào nội dung câu thông báo.

#### Realtime event

Mỗi event phải có:

- Tên event ổn định.
- `eventId`.
- `meetingId`.
- `occurredAt` theo UTC.
- `actorId` khi phù hợp.
- `version` hoặc sequence khi cần bảo toàn thứ tự.
- Payload có schema rõ ràng.

Client phải xử lý reconnect, đăng ký lại group và event trùng. Không giả định event luôn đến đúng thứ tự hoặc đúng một lần.

#### Background/AI job

Mỗi job phải có:

- `jobId` và `correlationId`.
- Loại job và schema version.
- Resource/meeting ID.
- Trạng thái bền vững.
- Số lần thử và lỗi gần nhất.
- Cơ chế idempotency.
- Timeout và chính sách retry.

### Bước 6 - Triển khai backend

Thứ tự mặc định:

1. Entity/value object và migration.
2. DTO/request/response model.
3. Validator.
4. Repository/query.
5. Domain/application service.
6. Authorization.
7. Controller/hub/job handler.
8. Unit/integration test.

Controller chỉ điều phối request/response. Không đặt nghiệp vụ phức tạp trong controller.

Mọi endpoint có `meetingId`, `documentId`, `recordingId`, `transcriptId` hoặc tài nguyên tương tự phải kiểm tra quyền theo chính tài nguyên đó, không chỉ kiểm tra người dùng đã đăng nhập.

### Bước 7 - Triển khai frontend Vue

Tách trách nhiệm theo cấu trúc phù hợp repository, ví dụ:

- `views/` hoặc `pages/`: màn hình theo route.
- `components/`: thành phần tái sử dụng.
- `stores/`: trạng thái dùng chung và action nghiệp vụ phía client.
- `services/` hoặc `api/`: HTTP/realtime client.
- `types/`: kiểu dữ liệu và contract.
- `composables/`: logic UI dùng lại.

Mỗi màn hình phải xử lý tối thiểu các trạng thái:

- Loading.
- Thành công/có dữ liệu.
- Rỗng.
- Lỗi có khả năng thử lại.
- Không có quyền.
- Mất kết nối/reconnecting nếu có realtime.

Các yêu cầu UI mặc định:

- Giao diện tiếng Việt.
- Responsive ưu tiên laptop/desktop từ 1366x768 đến Full HD.
- Màu chủ đạo `#3E83F8`, điểm nhấn `#973DEC`, nền nhạt `#EFF6FF` và trắng.
- Font Roboto hoặc Segoe UI, cỡ nội dung chính 14-16px.
- Có focus state, label rõ ràng và thao tác bàn phím cơ bản.
- Nút nguy hiểm phải có xác nhận; không dùng chỉ màu sắc để biểu đạt trạng thái.
- Không hiển thị action mà người dùng không có quyền; backend vẫn phải kiểm tra lại.

### Bước 8 - Tích hợp và xử lý lỗi

Đối với Jitsi, Jibri, OnlyOffice, Excalidraw, MinIO hoặc AI service:

- Cấu hình qua environment/config, không hard-code.
- Xác minh callback/webhook nếu dịch vụ hỗ trợ chữ ký.
- Đặt timeout cho mọi lời gọi mạng.
- Retry chỉ với lỗi tạm thời và thao tác an toàn/idempotent.
- Có trạng thái degraded/failure rõ ràng cho người dùng.
- Log correlation ID nhưng không log token, nội dung nhạy cảm hoặc dữ liệu media.
- Có cleanup/compensation khi CSDL thành công nhưng object storage/dịch vụ ngoài thất bại hoặc ngược lại.

### Bước 9 - Kiểm thử

Mỗi task phải có mức test tương xứng với rủi ro.

#### Tối thiểu phải kiểm tra

- Happy path.
- Dữ liệu không hợp lệ.
- Chưa đăng nhập.
- Đã đăng nhập nhưng không có quyền.
- Resource không tồn tại hoặc không thuộc phạm vi người dùng.
- Trạng thái cuộc họp không cho phép thao tác.
- Dữ liệu trùng/idempotency khi liên quan.
- Lỗi dịch vụ ngoài, timeout hoặc retry khi liên quan.

#### Với realtime

- Kết nối và reconnect.
- Join/leave đúng group cuộc họp.
- Event trùng hoặc đến sai thứ tự.
- Không rò rỉ event sang cuộc họp khác.

#### Với AI pipeline

- Job được enqueue đúng một cách logic.
- Retry không tạo transcript/biên bản trùng.
- Chuyển trạng thái đúng: queued, processing, completed, failed/cancelled.
- Có thể truy vết kết quả về recording và meeting nguồn.
- Nội dung AI được đánh dấu là bản nháp và có bước người dùng duyệt khi nghiệp vụ yêu cầu.

#### Với frontend

- Component/unit test cho logic quan trọng.
- Kiểm tra route guard và permission-based actions.
- Kiểm tra loading, empty, error và reconnect state.
- Chạy type-check, lint và build production.

### Bước 10 - Tự review trước bàn giao

Agent phải đọc lại diff và trả lời được:

- Code có đúng phạm vi task không?
- Có vô tình sửa file không liên quan không?
- Có lộ secret hoặc dữ liệu nhạy cảm không?
- Có endpoint thiếu kiểm tra quyền không?
- Có race condition hoặc thao tác không idempotent không?
- Có migration phá dữ liệu cũ không?
- API/event frontend và backend có khớp không?
- Error state có được xử lý không?
- Test có thực sự kiểm tra hành vi hay chỉ chạy qua?
- Tài liệu/cấu hình có cần cập nhật không?

### Bước 11 - Báo cáo bàn giao

Báo cáo cuối task phải ngắn gọn nhưng có đủ:

1. Kết quả đã hoàn thành.
2. Các file/khu vực chính đã thay đổi.
3. Quyết định kỹ thuật hoặc giả định đáng chú ý.
4. Lệnh kiểm tra đã chạy và kết quả.
5. Phần chưa làm, hạn chế hoặc rủi ro còn lại.
6. Cách chạy/kiểm tra thủ công nếu cần.

Không tuyên bố “hoàn thành” nếu build/test liên quan chưa chạy được. Nếu môi trường chặn kiểm thử, phải nói rõ lệnh cần chạy và nguyên nhân chưa xác minh.

---

## 6. Quy tắc nghiệp vụ xuyên suốt

### 6.1. Quyền và bảo mật

- Áp dụng nguyên tắc quyền tối thiểu.
- Phân biệt rõ quyền hệ thống, quyền theo cuộc họp và vai trò trong phiên họp.
- Chủ trì, thư ký, thành viên, khách và quản trị viên không mặc nhiên có cùng quyền.
- Quyền lưu dạng JSON phải có `schemaVersion`, giá trị mặc định an toàn và validation phía server.
- Không tin `userId`, role hoặc permission do frontend gửi lên.
- URL tải/xem tệp phải có thời hạn hoặc đi qua endpoint đã kiểm tra quyền.
- Các thao tác nhạy cảm cần audit log: thay đổi quyền, xóa dữ liệu, bắt đầu/dừng ghi, phát hành biên bản.

### 6.2. Trạng thái cuộc họp

Mọi thao tác phải kiểm tra trạng thái hiện tại và transition hợp lệ. Không suy luận trạng thái chỉ từ thời gian bắt đầu/kết thúc. Việc chuyển trạng thái phải được thực hiện atomically hoặc có concurrency guard khi có nguy cơ hai yêu cầu đồng thời.

### 6.3. Tệp và bản ghi

- Lưu metadata trước/sau upload theo quy trình có thể phục hồi.
- Kiểm tra loại tệp, kích thước và tên tệp.
- Không dùng tên tệp người dùng làm object key duy nhất.
- Có checksum khi cần bảo đảm toàn vẹn.
- Xóa logic trước khi xóa vật lý nếu nghiệp vụ cần khôi phục/audit.
- Ghi hình/ghi âm phải có thông báo và quyền phù hợp.

### 6.4. Transcript, biên bản và AI

- Transcript phải có version và liên kết đến recording nguồn.
- Segment nên lưu mốc thời gian và speaker khi có.
- Kết luận/quyết định/action item do AI tạo cần có dẫn nguồn về transcript segment khi khả thi.
- Biên bản AI là bản nháp cho đến khi người có thẩm quyền duyệt/phát hành.
- Lưu version của prompt, model/config và output để có thể tái lập, so sánh và đánh giá.
- Không ghi đè bản người dùng đã chỉnh sửa khi job AI retry.

### 6.5. Thời gian và định danh

- Lưu thời gian ở UTC; chuyển sang múi giờ người dùng khi hiển thị.
- ID do server tạo; ưu tiên định dạng nhất quán với codebase.
- Dùng correlation ID xuyên suốt API, job và microservice.

---

## 7. Quy tắc chất lượng code

- Tên biến, hàm, class và file bằng tiếng Anh; nội dung giao diện có thể bằng tiếng Việt.
- Hàm/class chỉ nên có một trách nhiệm rõ ràng.
- Tránh duplicate logic; trích xuất abstraction khi đã có nhu cầu thực tế.
- Không tạo abstraction chung chung chỉ để “dự phòng tương lai”.
- Comment giải thích lý do hoặc ràng buộc, không lặp lại điều code đã nói rõ.
- Public contract cần type rõ ràng; tránh `any` ở TypeScript và object động không kiểm soát ở backend.
- Validation ở boundary; invariant nghiệp vụ ở service/domain.
- Query danh sách phải có phân trang, sắp xếp ổn định và giới hạn kích thước.
- Tránh N+1 query; chỉ lấy field cần dùng.
- Dùng structured logging; không ghi PII, token, audio/transcript đầy đủ vào log.
- Mọi config khác nhau theo môi trường phải đi qua cấu hình, không sửa code.

---

## 8. Quản lý database và migration

- Không sửa migration đã được áp dụng; tạo migration mới.
- Migration phải có tên mô tả mục đích.
- Thay đổi phá vỡ dữ liệu phải có kế hoạch backfill/chuyển đổi và rollback.
- Cột mới bắt buộc trên bảng có dữ liệu cần default hoặc triển khai nhiều bước.
- Tạo index cho khóa ngoại và truy vấn quan trọng, nhưng tránh index dư thừa.
- Dữ liệu JSON phải có schema/version và validation.
- Seed phải idempotent, không phụ thuộc vào ID ngẫu nhiên không ổn định.

---

## 9. Git và an toàn khi sửa mã nguồn

- Luôn kiểm tra `git status` trước và sau khi sửa.
- Không ghi đè hoặc hoàn tác thay đổi của người dùng.
- Không dùng lệnh phá hủy như reset cứng hoặc xóa hàng loạt.
- Không commit, push, tạo PR hoặc deploy nếu người dùng chưa yêu cầu.
- Không gộp refactor lớn với feature/bugfix nhỏ nếu không cần thiết.
- Diff cuối phải sạch, tập trung và dễ review.

---

## 10. Definition of Ready

Một task sẵn sàng để code khi có đủ:

- Mục tiêu và actor rõ ràng.
- Phạm vi trong/ngoài task.
- Luồng chính và lỗi quan trọng.
- Permission cần thiết.
- Contract hoặc đủ thông tin để thiết kế contract.
- Tiêu chí nghiệm thu kiểm chứng được.
- Không còn quyết định nghiệp vụ lớn chưa được chốt.

Nếu chưa đạt, Agent phải làm rõ yêu cầu trước hoặc ghi rõ giả định được phép dùng.

---

## 11. Definition of Done

Một task chỉ được coi là hoàn thành khi:

- Chức năng đáp ứng tiêu chí nghiệm thu.
- Quyền và validation được kiểm tra phía server.
- UI có đầy đủ trạng thái cần thiết.
- API/event contract đồng nhất giữa các thành phần.
- Migration và dữ liệu tương thích được xử lý nếu có.
- Test liên quan đã thêm/cập nhật và chạy đạt.
- Lint, type-check và build liên quan chạy đạt.
- Không có lỗi nghiêm trọng trong self-review diff.
- Config/tài liệu được cập nhật khi cần.
- Báo cáo bàn giao nêu rõ kết quả xác minh và giới hạn còn lại.

---

## 12. Mẫu prompt giao việc cho AI Agent

Sử dụng mẫu sau khi giao một task:

```text
Hãy đọc toàn bộ WORKFLOW.md và các tài liệu đặc tả liên quan trước khi sửa code.

Task: [mô tả chức năng/lỗi]
Actor: [vai trò người dùng]
Mục tiêu: [kết quả người dùng cần]
Phạm vi: [module/màn hình/API]
Ngoài phạm vi: [những gì không làm]
Tiêu chí nghiệm thu:
1. [...]
2. [...]
3. [...]

Yêu cầu làm việc:
- Khảo sát code hiện tại và nêu kế hoạch ngắn trước khi sửa.
- Tuân theo kiến trúc và convention có sẵn.
- Không làm mất thay đổi hiện tại của tôi.
- Thực hiện đầy đủ validation, authorization và error states.
- Thêm/cập nhật test phù hợp.
- Chạy lint, type-check, test và build liên quan.
- Cuối cùng báo cáo file đã đổi, lệnh kiểm tra, kết quả và rủi ro còn lại.
```

---

## 13. Mẫu kế hoạch của Agent

```markdown
### Phạm vi hiểu được
- ...

### Tài liệu/code đã đối chiếu
- ...

### Giả định hoặc câu hỏi còn lại
- ...

### Kế hoạch
1. ...
2. ...
3. ...

### Cách xác minh
- ...
```

---

## 14. Mẫu báo cáo hoàn thành

```markdown
### Kết quả
- ...

### Thay đổi chính
- `path/to/file`: ...

### Kiểm tra đã chạy
- `command`: PASS/FAIL - ghi chú ngắn

### Quyết định/giả định
- ...

### Chưa hoàn thành hoặc rủi ro còn lại
- Không có / ...
```

---

## 15. Checklist nhanh trước khi Agent kết thúc

- [ ] Đã đọc workflow và đặc tả liên quan.
- [ ] Đã kiểm tra code hiện có trước khi tạo mới.
- [ ] Thay đổi đúng phạm vi.
- [ ] Backend kiểm tra quyền theo tài nguyên.
- [ ] Input được validation.
- [ ] Contract frontend/backend/realtime khớp nhau.
- [ ] Có loading, empty, error và permission states.
- [ ] Job/realtime có idempotency/reconnect khi liên quan.
- [ ] Không hard-code secret hoặc URL môi trường.
- [ ] Không log dữ liệu nhạy cảm.
- [ ] Test, lint, type-check và build liên quan đã chạy.
- [ ] Đã tự review diff.
- [ ] Báo cáo rõ kết quả và giới hạn.

---

## 16. Kết luận

AI Agent không chỉ có nhiệm vụ tạo ra code chạy được. Agent phải tạo ra thay đổi đúng nghiệp vụ, an toàn, có thể kiểm thử, phù hợp kiến trúc và đủ rõ để người khác tiếp tục bảo trì. Khi tốc độ xung đột với tính đúng đắn, bảo mật hoặc an toàn dữ liệu, phải ưu tiên tính đúng đắn, bảo mật và khả năng phục hồi.
