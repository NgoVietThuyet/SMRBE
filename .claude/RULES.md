# RULES - QUY ƯỚC CODE DỰ ÁN SMART MEETING

> Bộ luật kỹ thuật bắt buộc dành cho AI Agent và lập trình viên phát triển hệ thống Smart Meeting.

## 1. Mục đích và phạm vi áp dụng

Tài liệu này quy định cách tổ chức và viết mã nguồn để toàn dự án nhất quán, dễ đọc, dễ kiểm thử và dễ bảo trì. Tài liệu áp dụng cho:

- Frontend Vue 3 + TypeScript.
- Core API ASP.NET Core .NET 8 + Entity Framework Core.
- AI service Python + FastAPI.
- SQL Server, migration, Redis, MinIO/S3.
- REST API, SignalR/realtime event và background job.
- Unit test, integration test và end-to-end test.
- Cấu hình, log, Git và tài liệu kỹ thuật đi kèm mã nguồn.

`WORKFLOW.md` trả lời câu hỏi **phải làm việc theo quy trình nào**. `RULES.md` trả lời câu hỏi **mã nguồn phải được viết như thế nào**. AI Agent phải đọc cả hai file trước khi sửa code.

---

## 2. Thứ tự ưu tiên khi có xung đột

Áp dụng thứ tự ưu tiên sau:

1. Yêu cầu mới nhất và tiêu chí nghiệm thu của task.
2. `WORKFLOW.md` và quy định bảo mật của dự án.
3. Convention đã được dùng nhất quán trong module/repository hiện tại.
4. `RULES.md` này.
5. Đặc tả chức năng liên quan.
6. Convention mặc định của framework/ngôn ngữ.
7. Sở thích cá nhân của người viết code hoặc AI Agent.

Nếu repository đã có convention hợp lý và nhất quán khác với ví dụ trong file này, Agent phải đi theo repository, không được đổi tên hàng loạt chỉ để ép code cũ theo tài liệu. Nếu khác biệt ảnh hưởng public API, schema dữ liệu, bảo mật hoặc kiến trúc, phải hỏi chủ dự án trước khi thay đổi.

---

## 3. Nguyên tắc chung

### 3.1. Ngôn ngữ sử dụng

- Tên file, thư mục, class, interface, type, enum, hàm, biến và hằng số phải bằng tiếng Anh.
- Nội dung giao diện dành cho người dùng có thể dùng tiếng Việt.
- Comment và tài liệu kỹ thuật ưu tiên tiếng Việt rõ ràng; thuật ngữ kỹ thuật giữ nguyên tiếng Anh khi dịch gây khó hiểu.
- Mỗi file, mỗi hàm viết ra đều phải có comment giải thích rõ ràng, không cần quá chi tiết.
- Không dùng tiếng Việt không dấu để đặt identifier.
- Không trộn nhiều ngôn ngữ trong cùng một identifier.

```ts
// Đúng
const meetingParticipants = ref<MeetingParticipant[]>([])
const isRecording = computed(() => recording.value?.status === 'recording')

// Sai
const danhSachThanhVien = []
const dsThanhVien = []
const meetingThanhVien = []
```

### 3.2. Tên phải thể hiện ý nghĩa

- Ưu tiên tên đầy đủ, có nghĩa; tránh viết tắt không phổ biến.
- Tên phải mô tả vai trò nghiệp vụ, không chỉ mô tả kiểu dữ liệu.
- Không dùng tên mơ hồ như `data`, `item`, `obj`, `temp`, `info`, `value`, `result` ngoài phạm vi rất ngắn và hiển nhiên.
- Được dùng các viết tắt phổ biến của dự án như `id`, `url`, `api`, `dto`, `utc`, `ai`, `stt`.
- Một khái niệm phải có một tên nhất quán xuyên suốt frontend, backend, event và tài liệu.

```csharp
// Đúng
var activeParticipants = await participantRepository.GetActiveByMeetingIdAsync(meetingId, cancellationToken);

// Sai
var data = await repo.GetData(id);
```

### 3.3. Một đơn vị code, một trách nhiệm

- Một class/component/hàm chỉ đảm nhiệm một trách nhiệm chính.
- Controller không chứa nghiệp vụ phức tạp.
- Vue component không trực tiếp điều phối nhiều API và realtime subscription phức tạp.
- Repository không quyết định quyền hay quy tắc nghiệp vụ.
- Utility không được trở thành nơi gom mọi hàm không biết đặt ở đâu.
- Khi một file quá dài hoặc có nhiều lý do để thay đổi, tách theo trách nhiệm, không tách chỉ để đạt số dòng tùy ý.

### 3.4. Quy tắc về độ phức tạp

- Hàm nên ngắn và đọc được theo một luồng từ trên xuống.
- Ưu tiên guard clause để giảm lồng `if`.
- Tránh lồng điều kiện quá 3 cấp.
- Tách nghiệp vụ có tên rõ ràng thay vì viết một biểu thức dài khó hiểu.
- Không tối ưu sớm nếu chưa có bằng chứng; nhưng không chấp nhận N+1 query hoặc tải toàn bộ bảng để lọc trong bộ nhớ.
- Không dùng “magic number” hoặc “magic string”; đưa vào constant, enum hoặc config có tên.

---

## 4. Quy ước đặt tên tổng quát

| Thành phần | Quy ước | Ví dụ |
| --- | --- | --- |
| Class, record, interface/type | `PascalCase` | `MeetingService`, `MeetingSummaryDto` |
| Hàm/method | Theo chuẩn ngôn ngữ | `createMeeting`, `CreateMeetingAsync` |
| Biến/tham số | `camelCase` | `meetingId`, `currentParticipant` |
| Boolean | Bắt đầu bằng `is`, `has`, `can`, `should`, `was` | `isLoading`, `hasPermission`, `canRecord` |
| Collection | Danh từ số nhiều | `meetings`, `participantIds` |
| Constant TypeScript | `UPPER_SNAKE_CASE` | `MAX_UPLOAD_SIZE_BYTES` |
| Constant C# | `PascalCase` theo .NET | `MaxUploadSizeBytes` |
| Enum/type | Danh từ số ít | `MeetingStatus`, `RecordingState` |
| Event | Quá khứ, dạng `domain.action` | `meeting.started`, `file.uploaded` |
| REST resource | Danh từ số nhiều, `kebab-case` nếu nhiều từ | `/meeting-minutes`, `/action-items` |
| Database table | Theo convention EF/codebase | `MeetingInfo`, `MeetingParticipants` |
| Environment variable | `UPPER_SNAKE_CASE` | `MINIO_ENDPOINT` |

### 4.1. Boolean

Tên boolean phải đọc được như một câu hỏi đúng/sai.

```ts
// Đúng
const isMeetingLocked = ref(false)
const hasRecordingPermission = computed(() => permissions.value.canRecord)

// Sai
const meetingLock = false
const recordingPermission = true
const checkPermission = true
```

Hàm trả về boolean dùng động từ như `is`, `has`, `can`, `should` hoặc `exists`:

```csharp
bool CanStartMeeting(Meeting meeting, UserContext userContext)
Task<bool> MeetingCodeExistsAsync(string meetingCode, CancellationToken cancellationToken)
```

### 4.2. Hàm và command

- Tên hàm bắt đầu bằng động từ và nêu kết quả nghiệp vụ.
- Hàm đọc dữ liệu: `get`, `find`, `list`, `search`, `load`.
- Hàm thay đổi trạng thái: `create`, `update`, `start`, `end`, `approve`, `publish`, `cancel`.
- Không dùng tên chung chung như `handleData`, `process`, `doAction`, `executeTask` nếu có thể đặt tên chính xác hơn.
- Event handler frontend có thể dùng `handleXxx`; callback DOM ngắn có thể dùng `onXxx`.

### 4.3. Collection và số lượng

- Collection dùng danh từ số nhiều: `meetingIds`, `transcriptSegments`.
- Số lượng dùng hậu tố `Count`: `participantCount`.
- Chỉ số dùng hậu tố `Index`: `segmentIndex`.
- Tổng dung lượng dùng đơn vị trong tên: `fileSizeBytes`, `durationSeconds`.
- Thời điểm và khoảng thời gian phải phân biệt: `startedAtUtc` và `durationSeconds`.

### 4.4. ID và từ viết tắt

- TypeScript: `meetingId`, `apiClient`, `recordingUrl`.
- C#: `MeetingId`, `ApiClient`, `RecordingUrl`; dùng `Id`, không dùng `ID` trừ convention codebase yêu cầu.
- Python: `meeting_id`, `api_client`, `recording_url`.
- Không dùng `idMeeting`, `meetingID`, `MeetingID` lẫn lộn.

---

## 5. Quy ước file và thư mục chung

### 5.1. Quy tắc bắt buộc

- Mỗi file có một trách nhiệm chính và tên khớp với nội dung chính.
- Không tạo file tên `common`, `helper`, `utils` ở cấp rộng nếu không định nghĩa rõ miền trách nhiệm.
- Code liên quan một feature nên đặt gần nhau theo module/feature nếu cấu trúc repository cho phép.
- Không tạo thư mục chỉ chứa một file nếu không có khả năng trở thành một ranh giới module thật sự.
- Không tạo bản sao như `MeetingService2`, `MeetingServiceNew`, `MeetingServiceFinal`.
- File generated phải nằm ở vị trí riêng và không sửa thủ công.
- Không đổi tên hoa/thường chỉ trên hệ điều hành không phân biệt hoa/thường mà không kiểm tra Git.

### 5.2. Tên file Vue/TypeScript

| Loại file | Quy ước | Ví dụ |
| --- | --- | --- |
| Vue component/page | `PascalCase.vue` | `MeetingRoomView.vue`, `ParticipantList.vue` |
| Composable | `useXxx.ts` | `useMeetingRealtime.ts` |
| Pinia store | `xxx.store.ts` | `meeting.store.ts` |
| API service | `xxx.api.ts` | `meeting.api.ts` |
| Type/model | `xxx.types.ts` | `meeting.types.ts` |
| Schema/validation | `xxx.schema.ts` | `meeting.schema.ts` |
| Utility chuyên biệt | `xxx.utils.ts` | `date-time.utils.ts` |
| Unit test | `*.spec.ts` | `meeting.store.spec.ts` |
| E2E test | `*.e2e.spec.ts` | `create-meeting.e2e.spec.ts` |

### 5.3. Tên file C#

- Tên file trùng với public type chính: `MeetingService.cs` chứa `MeetingService`.
- Interface: `IMeetingService.cs`.
- DTO theo mục đích: `CreateMeetingRequest.cs`, `MeetingDetailResponse.cs`.
- Validator: `CreateMeetingRequestValidator.cs`.
- Controller: `MeetingsController.cs`.
- Test: `MeetingServiceTests.cs`, `CreateMeetingEndpointTests.cs`.
- Không gom nhiều public class không liên quan trong một file.

### 5.4. Tên file Python

- Module và package dùng `snake_case`: `transcript_service.py`, `speaker_diarization/`.
- Test dùng `test_*.py`: `test_transcript_service.py`.
- Pydantic schema đặt trong module có tên rõ nghĩa: `meeting_minutes_schemas.py`.
- Không đặt tên module trùng thư viện chuẩn như `logging.py`, `typing.py`, `json.py`.

---

## 6. Cấu trúc frontend Vue 3

### 6.1. Cấu trúc tham khảo

```text
src/
├── app/                    # bootstrap, router, plugin toàn ứng dụng
├── assets/                 # ảnh, font và style tĩnh
├── components/             # component dùng chung thực sự
├── composables/            # logic UI có thể tái sử dụng
├── features/
│   └── meetings/
│       ├── api/
│       ├── components/
│       ├── composables/
│       ├── pages/
│       ├── stores/
│       ├── types/
│       └── tests/
├── layouts/
├── router/
├── services/               # hạ tầng dùng chung: HTTP, realtime
├── stores/                 # state toàn ứng dụng: auth, notification
├── styles/
├── types/
└── utils/                  # utility thuần, dùng chung thật sự
```

Đây là cấu trúc mặc định cho code mới. Nếu repository đang có cấu trúc khác nhưng nhất quán, mở rộng cấu trúc hiện có; không tự ý di chuyển toàn bộ project.

### 6.2. Component Vue

- Dùng Vue 3 Composition API, TypeScript và `<script setup lang="ts">`.
- Thứ tự trong file: `<script setup>`, `<template>`, `<style scoped>` trừ convention formatter hiện tại.
- Props và emits phải có type rõ ràng.
- Không sửa trực tiếp prop.
- Không đặt business rule quan trọng chỉ trong template.
- Template không gọi hàm tốn kém hoặc tạo object mới lặp lại mỗi lần render.
- Component trình bày nhận dữ liệu và phát event; page/container điều phối dữ liệu.
- Component tái sử dụng không được tự phụ thuộc vào route hiện tại nếu không phải trách nhiệm của nó.
- Tên component gồm nhiều từ, trừ component gốc như `App.vue`.

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { MeetingParticipant } from '../types/meeting.types'

const props = defineProps<{
  participant: MeetingParticipant
  canManage: boolean
}>()

const emit = defineEmits<{
  remove: [participantId: string]
}>()

const displayRole = computed(() => props.participant.roleLabel)

function handleRemove(): void {
  emit('remove', props.participant.id)
}
</script>
```

### 6.3. Props, emits và `v-model`

- Props dùng `camelCase` trong script và `kebab-case` trong template.
- Event component mô tả việc đã xảy ra: `saved`, `removed`, `role-changed`.
- Không phát event mơ hồ như `change` khi có nhiều loại thay đổi.
- Custom `v-model` dùng `modelValue`/`update:modelValue` hoặc named model rõ nghĩa.
- Payload event phải nhỏ, có type và ổn định.

### 6.4. Composable

- Tên bắt đầu bằng `use`: `useMeetingPermissions`, `useRecordingStatus`.
- Composable phải có phạm vi và vòng đời rõ ràng.
- Subscription hoặc event listener phải được cleanup khi unmount.
- Không ẩn side effect lớn trong composable có tên như một hàm đọc dữ liệu.
- Không dùng composable làm kho state toàn cục thay cho Pinia nếu state cần chia sẻ bền vững giữa nhiều route.

### 6.5. Pinia store

- Mỗi store đại diện một miền state rõ ràng.
- State giữ dữ liệu dùng chung; state chỉ dùng trong một component nên để tại component.
- Action dùng tên nghiệp vụ như `loadMeeting`, `startRecording`; không dùng `setData` chung chung.
- Getter là dữ liệu dẫn xuất, không gây side effect.
- Không giữ `AbortController`, DOM element hoặc object không serializable trong state nếu không có lý do rõ ràng.
- Reset state khi logout hoặc rời miền dữ liệu nhạy cảm.
- Không nuốt lỗi trong store; chuyển lỗi thành state có type hoặc ném lại cho UI xử lý.

### 6.6. API client frontend

- Mọi HTTP call đi qua API client/service tập trung.
- Component không trực tiếp gọi `fetch`/`axios`.
- Request/response có type; không dùng `any`.
- Chuẩn hóa base URL, token, refresh, correlation ID và error mapping ở interceptor/client chung.
- Hủy request khi không còn cần nếu có nguy cơ response cũ ghi đè state mới.
- Không tự đổi casing contract trong nhiều nơi; dùng mapper tập trung nếu frontend/backend khác convention.
- Không hiển thị trực tiếp message kỹ thuật từ server cho người dùng.

### 6.7. Router và quyền

- Route name dùng `PascalCase` hoặc convention hiện tại một cách nhất quán.
- Path dùng `kebab-case`, danh từ và cấu trúc tài nguyên rõ ràng.
- Route guard kiểm tra authentication/khả năng truy cập để cải thiện UX.
- Ẩn/disable action theo quyền ở UI, nhưng không xem đây là lớp bảo mật.
- Backend luôn phải kiểm tra lại authorization theo tài nguyên.
- Không dựa vào query string chứa `role` hoặc `userId` để cấp quyền.

### 6.8. CSS và giao diện

- Ưu tiên design token/CSS variable cho màu, spacing, radius và typography.
- Không lặp literal màu ở nhiều component.
- Class CSS dùng `kebab-case`; áp dụng BEM hoặc convention hiện tại nhất quán.
- Tránh selector sâu phụ thuộc cấu trúc DOM nội bộ của component khác.
- Không dùng `!important` trừ trường hợp tích hợp thư viện và có comment giải thích.
- Mỗi màn hình phải có loading, empty, error, forbidden và success state phù hợp.
- Action chỉ có icon phải có `aria-label`/tooltip.
- Form control phải có label, thông báo lỗi và focus state.
- Responsive tối thiểu từ 1366x768 đến Full HD theo đặc tả dự án.

---

## 7. Cấu trúc backend ASP.NET Core .NET 8

### 7.1. Phân lớp tham khảo

```text
src/
├── SmartMeeting.Api/             # controller, middleware, composition root
├── SmartMeeting.Application/     # use case, DTO, validator, interface
├── SmartMeeting.Domain/          # entity, value object, domain rule/event
├── SmartMeeting.Infrastructure/  # EF Core, MinIO, Redis, external service
└── SmartMeeting.Worker/          # background job nếu tách process
tests/
├── SmartMeeting.UnitTests/
├── SmartMeeting.IntegrationTests/
└── SmartMeeting.ArchitectureTests/
```

Không bắt buộc tái cấu trúc repository cũ theo sơ đồ trên. Mục tiêu là giữ dependency hướng vào domain/application và không để nghiệp vụ phụ thuộc framework/hạ tầng không cần thiết.

### 7.2. Naming C#/.NET

- Namespace, class, record, property, public method dùng `PascalCase`.
- Local variable và parameter dùng `camelCase`.
- Private field dùng `_camelCase`.
- Interface bắt đầu bằng `I`.
- Method async có hậu tố `Async`, trừ entry point/framework callback theo convention của framework.
- `CancellationToken` đặt cuối danh sách tham số và truyền xuyên suốt I/O async.
- Acronym dùng casing theo .NET: `MeetingId`, `RecordingUrl`, `ApiClient`.

### 7.3. Controller/endpoint

- Controller dùng danh từ số nhiều: `MeetingsController`.
- Controller chỉ nhận request, gọi application service/use case và ánh xạ HTTP response.
- Không truy cập `DbContext` trực tiếp từ controller.
- Không đặt business rule trong attribute, controller hoặc mapper.
- Endpoint phải khai báo authorization rõ ràng; endpoint public phải có lý do.
- Dùng HTTP status đúng ngữ nghĩa và error response có machine-readable code.
- Không trả EF entity trực tiếp ra API.
- Không nhận `CreatedBy`, `OwnerId`, role hoặc permission từ client nếu server có thể suy ra từ identity.

### 7.4. Service/use case

- Tên service theo miền: `MeetingLifecycleService`, không phải `CommonService`.
- Public method tương ứng một hành động nghiệp vụ rõ ràng.
- Validation hình thức ở request validator; invariant nghiệp vụ được bảo vệ trong domain/application service.
- Transaction boundary nằm tại use case/application service, không rải rác ở controller.
- Không catch `Exception` chỉ để trả `false` hoặc `null`.
- Không dùng exception cho luồng nghiệp vụ dự kiến nếu project đã có Result/error type phù hợp.
- Thao tác thay đổi trạng thái phải kiểm tra state transition và concurrency.

### 7.5. Entity và domain model

- Entity bảo vệ invariant quan trọng; không để mọi property `public set` nếu có thể tạo trạng thái sai.
- Enum/value object dùng cho khái niệm có tập giá trị hoặc luật riêng.
- Thời gian lưu UTC và tên property phải rõ nghĩa: `StartedAtUtc` hoặc convention UTC toàn project đã được tài liệu hóa.
- Audit field nhất quán: `CreatedAt`, `CreatedBy`, `UpdatedAt`, `UpdatedBy`, `DeletedAt`.
- Không nhồi JSON tùy ý; JSON phải có typed model, `SchemaVersion` và validation.
- Không dùng navigation property để vô tình serialize toàn bộ object graph.

### 7.6. DTO và mapping

- Request/response tách khỏi entity.
- Tên DTO thể hiện chiều và mục đích: `CreateMeetingRequest`, `MeetingDetailResponse`.
- Không tạo `MeetingDto` khổng lồ dùng cho mọi endpoint.
- Chỉ trả field client cần và người dùng có quyền xem.
- Mapping đơn giản có thể viết trực tiếp; mapping phức tạp phải có test.
- Không để AutoMapper hoặc reflection che giấu business rule.
- Contract public không dùng type mơ hồ như `object`, dictionary không schema hoặc dynamic.

### 7.7. Entity Framework Core và repository/query

- Query chỉ đọc dùng `AsNoTracking()` khi phù hợp.
- Project dữ liệu thành DTO ở database thay vì tải entity graph dư thừa.
- Tránh N+1 query; kiểm tra SQL với query phức tạp.
- Danh sách phải phân trang, có giới hạn và thứ tự ổn định.
- Không gọi `ToListAsync()` trước khi hoàn tất filter/sort/projection.
- Dùng eager loading có chủ đích; không `Include` toàn bộ quan hệ theo thói quen.
- Mọi query tài nguyên phải gắn scope quyền/tenant/meeting phù hợp.
- Dùng optimistic concurrency cho tài nguyên có thể được sửa đồng thời.

### 7.8. Dependency injection

- Đăng ký dependency theo lifetime đúng: singleton không giữ scoped service/DbContext.
- Constructor chỉ nhận dependency thật sự cần thiết.
- Không dùng service locator hoặc gọi `IServiceProvider` tùy ý trong business code.
- External client dùng typed client/factory và config có validation.
- Không new trực tiếp client hạ tầng ở application service.

### 7.9. Nullability và lỗi

- Bật nullable reference types và xử lý warning thay vì dùng `!` tùy tiện.
- `null` phải có nghĩa rõ ràng; dùng empty collection thay cho collection null khi phù hợp.
- `NotFound`, `Forbidden`, `Conflict`, `Validation` phải phân biệt bằng error code/status.
- Không để stack trace, SQL hoặc nội dung exception nội bộ ra response production.
- Log lỗi một lần tại boundary phù hợp; tránh cùng một lỗi bị log lặp qua nhiều tầng.

---

## 8. Quy tắc Python và FastAPI cho AI service

### 8.1. Style và naming

- Tuân thủ PEP 8 và formatter/linter của repository.
- Module, function, variable: `snake_case`.
- Class/Pydantic model: `PascalCase`.
- Constant: `UPPER_SNAKE_CASE`.
- Hàm async chỉ dùng khi thực sự chờ I/O async; không đánh dấu async cho CPU-bound code rồi block event loop.
- Public function phải có type hint; tránh `Any` nếu schema đã biết.

### 8.2. Cấu trúc tham khảo

```text
app/
├── api/              # router và dependency HTTP
├── core/             # config, logging, security
├── models/           # internal/domain model
├── schemas/          # Pydantic request/response/event
├── services/         # STT, diarization, summarization
├── repositories/     # metadata/persistence nếu có
├── jobs/             # worker handlers
└── integrations/     # model server, object storage, Core API
tests/
```

### 8.3. FastAPI endpoint

- Router mỏng; không chạy inference dài trong HTTP request.
- Request/response dùng Pydantic model có version khi là internal contract quan trọng.
- Tác vụ dài trả `202 Accepted` cùng `jobId` và trạng thái có thể truy vấn.
- Xác thực request nội bộ bằng cơ chế đã thống nhất; không tin callback chỉ vì đến từ mạng nội bộ.
- Đặt timeout khi gọi model server, object storage hoặc Core API.
- Không tải toàn bộ file media lớn vào memory nếu có thể streaming.

### 8.4. Pipeline AI

- Mỗi job có `job_id`, `correlation_id`, `meeting_id`, `schema_version` và idempotency key.
- Output liên kết được với recording, segment và phiên bản model/prompt.
- Retry không tạo transcript, minutes hoặc file trùng.
- Không ghi đè nội dung người dùng đã chỉnh sửa.
- Tách lỗi tạm thời có thể retry khỏi lỗi dữ liệu/model không nên retry.
- CPU/GPU-bound task chạy ở worker/process phù hợp, không block API worker.
- Kết quả AI là bản nháp cho đến khi người có quyền duyệt theo nghiệp vụ.

---

## 9. Quy tắc REST API

### 9.1. Route và HTTP method

- Route dùng resource, không dùng động từ tùy ý: `GET /meetings/{meetingId}`.
- Hành động domain không biểu diễn tốt bằng CRUD có thể dùng subresource/action rõ nghĩa: `POST /meetings/{meetingId}/start`.
- Dùng `GET` để đọc, `POST` để tạo/command, `PUT` để thay thế, `PATCH` để cập nhật một phần, `DELETE` để xóa.
- Route public phải được version theo convention project nếu có.
- Dùng cùng tên resource giữa route, DTO, permission và frontend type.

### 9.2. Request và response

- JSON field theo convention contract hiện tại; mặc định web API dùng `camelCase`.
- ID tài nguyên cha ở route; không lặp trong body nếu không cần.
- Validate độ dài, định dạng, khoảng giá trị và quan hệ giữa các field.
- Thời gian truyền theo ISO 8601 và UTC khi lưu/truyền liên dịch vụ.
- Pagination dùng contract thống nhất, có `items`, `page`/`cursor` và metadata cần thiết.
- Response không lộ field nội bộ, secret, object key hoặc thông tin người dùng không có quyền xem.

### 9.3. Error contract

Error phải có mã ổn định cho máy và message phù hợp người dùng hoặc frontend mapping:

```json
{
  "code": "MEETING_TIME_CONFLICT",
  "message": "Thời gian họp bị trùng với một lịch khác.",
  "traceId": "correlation-id",
  "details": {
    "field": "expectedStartTime"
  }
}
```

- Không để frontend phân tích chuỗi message để quyết định logic.
- Không trả `200 OK` với `success: false` cho lỗi HTTP thông thường.
- Validation error phải chỉ rõ field theo contract nhất quán.
- `403` dùng khi đã xác thực nhưng không có quyền; `404` có thể dùng để tránh lộ sự tồn tại của tài nguyên theo chính sách bảo mật.

### 9.4. Idempotency và concurrency

- Command dễ bị gửi lại như start/stop recording, tạo job AI hoặc upload finalize phải có idempotency strategy.
- Không retry mù quáng thao tác không idempotent.
- Dùng version/ETag/concurrency token cho tài nguyên chỉnh sửa đồng thời nếu phù hợp.
- Conflict phải trả `409` với error code rõ ràng.

---

## 10. Quy tắc SignalR và realtime event

- Tên event ổn định, dùng quá khứ: `participant.joined`, `recording.started`.
- Không dùng tên event UI như `refreshScreen` hoặc `showPopup`.
- Mọi event dùng envelope thống nhất gồm `eventId`, `type`, `meetingId`, `occurredAt`, `actorId`, `version`/sequence khi cần và `payload` có schema.
- Payload chỉ chứa dữ liệu cần thiết; dữ liệu nhạy cảm phải được lọc theo người nhận.
- Server quyết định group; không cho client tự join meeting group chỉ bằng ID mà không kiểm tra quyền.
- Client phải xử lý reconnect, đăng ký lại group, event trùng và event sai thứ tự.
- Event handler phải idempotent hoặc có deduplication bằng `eventId`.
- Thay đổi bền vững ghi database trước rồi mới phát event; nếu cần bảo đảm giao nhận, dùng outbox pattern.
- Không dùng realtime event thay cho nguồn dữ liệu bền vững; khi reconnect client phải có cách đồng bộ lại state.

---

## 11. Database, migration và dữ liệu

### 11.1. Naming và schema

- Theo convention EF Core/codebase hiện có; không trộn số ít/số nhiều tùy ý.
- Tên cột thể hiện đơn vị và ý nghĩa: `DurationSeconds`, `FileSizeBytes`.
- Foreign key dùng `<Entity>Id`.
- Boolean dùng `Is`/`Has`/`Can` theo nghĩa dữ liệu.
- Trạng thái dùng enum/string/code có tập giá trị được kiểm soát; không dùng số magic rải rác.
- JSON column có typed schema, `SchemaVersion`, default an toàn và validation phía server.

### 11.2. Migration

- Không sửa migration đã được áp dụng; tạo migration mới.
- Tên migration mô tả thay đổi: `AddMeetingMinutesApprovalFields`.
- Mỗi migration tập trung một thay đổi logic.
- Thêm cột bắt buộc vào bảng có dữ liệu phải có default/backfill hoặc triển khai nhiều bước.
- Migration phá vỡ dữ liệu phải có kế hoạch chuyển đổi, rollback và backup.
- Không xóa/đổi tên cột production như một thao tác một bước nếu phiên bản cũ còn sử dụng.
- Kiểm tra script migration và khả năng chạy trên dữ liệu hiện có.

### 11.3. Index và truy vấn

- Index foreign key và trường lọc/sort thường xuyên khi có bằng chứng truy vấn.
- Unique index bảo vệ invariant có thể bảo vệ ở database.
- Tránh index trùng hoặc quá rộng.
- Query phân trang phải có order ổn định, thường thêm ID làm tie-breaker.
- Không dùng `%keyword%` trên bảng lớn mà không xem xét chiến lược tìm kiếm/index.

### 11.4. Xóa và audit

- Tài nguyên cần khôi phục/truy vết dùng soft delete theo nghiệp vụ.
- Query mặc định phải loại dữ liệu đã soft delete.
- Hard delete chỉ thực hiện khi chính sách cho phép và đã xử lý object storage/relation liên quan.
- Ghi audit cho thay đổi quyền, xóa dữ liệu, bắt đầu/dừng ghi, duyệt/phát hành biên bản.
- Audit log không chứa token, nội dung media hoặc dữ liệu nhạy cảm dư thừa.

---

## 12. Bảo mật và quyền

- Không tin dữ liệu định danh/quyền từ frontend.
- Mọi endpoint kiểm tra authentication và authorization theo đúng tài nguyên.
- Áp dụng nguyên tắc quyền tối thiểu.
- Phân biệt quyền hệ thống, quyền theo cuộc họp và vai trò trong phiên.
- Validate input tại boundary; dùng parameterized query/EF Core, không nối chuỗi SQL từ input.
- Encode/sanitize nội dung hiển thị; đặc biệt chat, transcript, tên file và whiteboard content.
- Chống CSRF theo cơ chế auth đang dùng; cấu hình CORS theo allowlist cụ thể.
- Secret chỉ đến từ secret manager/environment/config bảo mật; không commit vào Git.
- URL MinIO/S3 phải có thời hạn hoặc đi qua endpoint đã kiểm tra quyền.
- Upload phải kiểm tra size, MIME, extension, object key và malware policy nếu có.
- Không log access token, refresh token, mật khẩu, full transcript/audio/video hoặc presigned URL.
- Không dùng ID tuần tự làm bằng chứng authorization.
- Webhook/callback phải xác minh chữ ký hoặc identity và chống replay nếu phù hợp.

---

## 13. Logging, monitoring và xử lý lỗi

- Dùng structured logging với property có tên, không nối chuỗi tùy ý.
- Mọi request/job liên dịch vụ có `correlationId`/`traceId`.
- Log mức `Information` cho sự kiện nghiệp vụ quan trọng, `Warning` cho trạng thái bất thường có thể phục hồi, `Error` cho lỗi cần điều tra.
- Không log cùng một exception ở mọi tầng.
- Log phải nêu context tối thiểu như resource ID và operation, nhưng không chứa dữ liệu nhạy cảm.
- Không dùng `console.log`/`print` trong production code; dùng logger chuẩn.
- External call phải có timeout và metric/log về latency, status phù hợp.
- Retry có giới hạn, exponential backoff và jitter khi phù hợp.
- Không catch lỗi rồi bỏ qua; nếu cố ý degraded mode phải có comment, log và UI state tương ứng.

```csharp
logger.LogInformation(
    "Meeting {MeetingId} started by user {UserId}",
    meetingId,
    userContext.UserId);
```

---

## 14. Kiểm thử

### 14.1. Quy tắc đặt tên test

- C# ưu tiên: `MethodName_Scenario_ExpectedResult`.
- TypeScript ưu tiên câu mô tả hành vi trong `describe`/`it`.
- Python ưu tiên: `test_<behavior>_<condition>`.
- Tên test phải nói rõ hành vi, điều kiện và kết quả; không dùng `Test1`, `works`, `shouldPass`.

```csharp
[Fact]
public async Task StartMeetingAsync_WhenUserIsNotHost_ReturnsForbidden()
```

```ts
it('hides the recording action when the participant lacks permission', () => {})
```

### 14.2. Cấu trúc test

- Một test kiểm tra một hành vi chính.
- Dùng Arrange-Act-Assert hoặc Given-When-Then nhất quán.
- Không phụ thuộc thứ tự chạy test.
- Không dùng thời gian thực, random không seed hoặc dịch vụ ngoài thật trong unit test.
- Fixture/builder có tên nghiệp vụ; tránh setup dài che mất mục tiêu test.
- Mock boundary bên ngoài, không mock mọi chi tiết nội bộ.
- Không chỉ kiểm tra method được gọi; ưu tiên kiểm tra state/output/side effect quan sát được.

### 14.3. Mức test tối thiểu

- Unit test cho rule, state transition, mapper/logic quan trọng.
- Integration test cho endpoint, authorization, database constraint và transaction.
- Component test cho loading/empty/error/permission state.
- E2E test cho luồng giá trị cao: đăng nhập, tạo lịch, tham gia, kết thúc, xem biên bản.
- Realtime test cho group isolation, reconnect và deduplication.
- AI job test cho idempotency, retry và không ghi đè nội dung người dùng.

### 14.4. Dữ liệu test

- Không dùng dữ liệu cá nhân thật.
- Dùng timestamp/ID xác định được khi test yêu cầu so sánh chính xác.
- Test database phải cô lập và cleanup an toàn.
- Không để test chỉ chạy được trên máy người viết.

---

## 15. Comment và tài liệu trong code

- Code tốt thể hiện “đang làm gì”; comment giải thích “vì sao” hoặc ràng buộc không hiển nhiên.
- Không comment lặp lại từng dòng code.
- Comment TODO phải có lý do/phạm vi; nếu hệ thống có issue tracker, kèm mã issue.
- Không để code bị comment-out; xóa và dùng Git để lưu lịch sử.
- Public API phức tạp cần XML doc/docstring theo convention project.
- Quyết định kiến trúc quan trọng nên ghi ADR hoặc tài liệu riêng, không chôn trong comment dài.
- Khi thay đổi config, contract hoặc cách chạy, cập nhật README/tài liệu liên quan cùng task.

---

## 16. Dependency và cấu hình

- Tìm khả năng có sẵn trước khi thêm package mới.
- Chỉ thêm dependency có mục đích rõ, được duy trì và tương thích license/phiên bản.
- Không thêm hai thư viện làm cùng một việc nếu không có lý do.
- Lockfile phải được cập nhật cùng dependency.
- Không nâng cấp hàng loạt dependency trong feature nhỏ.
- Mọi config theo môi trường đi qua options/env; không hard-code URL, bucket, model name, timeout hoặc credential.
- Config bắt buộc phải được validate khi khởi động và lỗi phải dễ hiểu.
- Có giá trị mặc định chỉ khi mặc định đó an toàn.
- File `.env.example` chỉ chứa tên biến và giá trị giả, không chứa secret thật.

---

## 17. Git và phạm vi thay đổi

- Kiểm tra `git status` trước và sau khi sửa.
- Không ghi đè thay đổi chưa commit của người dùng.
- Không format hoặc đổi tên file không liên quan.
- Không trộn refactor lớn với feature/bugfix nhỏ.
- Không commit generated build output, secret, log, media hoặc file tạm.
- Tên branch/commit theo convention repository; nếu chưa có, commit message dùng động từ mệnh lệnh, mô tả một thay đổi logic.
- Không commit, push, tạo PR, merge hoặc deploy nếu người dùng chưa yêu cầu.
- Không dùng lệnh Git phá hủy để làm sạch worktree.

Ví dụ commit message nếu được yêu cầu:

```text
feat(meetings): add host permission checks for recording
fix(transcripts): prevent duplicate segments during job retry
test(minutes): cover approval state transitions
```

---

## 18. Các hành vi AI Agent bị cấm

AI Agent không được:

1. Tạo code trước khi đọc `WORKFLOW.md`, `RULES.md`, đặc tả liên quan và code tương tự.
2. Tự ý thay đổi stack công nghệ hoặc kiến trúc chính.
3. Tự ý refactor/đổi tên hàng loạt ngoài phạm vi task.
4. Tạo API giả ở frontend không khớp backend.
5. Dùng `any`, `dynamic`, `object` hoặc dictionary không schema để né thiết kế type.
6. Hard-code token, mật khẩu, URL môi trường, user ID, meeting ID hoặc permission.
7. Bỏ authorization/validation để tính năng “chạy được”.
8. Trả entity trực tiếp hoặc lộ internal exception ra API.
9. Nuốt lỗi, dùng empty catch hoặc báo thành công khi thao tác thực tế thất bại.
10. Sửa migration đã chạy hoặc xóa dữ liệu mà không có kế hoạch an toàn.
11. Dùng mock/fake data trong production path khi không được yêu cầu.
12. Copy-paste logic sang nhiều nơi thay vì dùng ranh giới phù hợp.
13. Tạo abstraction chung chung cho nhu cầu chưa tồn tại.
14. Bỏ qua loading, empty, error, forbidden và reconnect state ở UI.
15. Tuyên bố hoàn thành khi chưa chạy kiểm tra liên quan hoặc chưa nói rõ lý do không thể chạy.
16. Thêm dependency chỉ vì quen dùng mà không kiểm tra thư viện hiện có.
17. Ghi log secret, PII, transcript đầy đủ hoặc media.
18. Commit, push, deploy hoặc xóa file ngoài yêu cầu.

---

## 19. Checklist trước khi hoàn thành một task code

### Naming và cấu trúc

- [ ] Tên file, class, hàm, biến bằng tiếng Anh và đúng casing.
- [ ] Tên boolean, collection, thời gian và đơn vị đo rõ nghĩa.
- [ ] File/class/component chỉ có một trách nhiệm chính.
- [ ] Code đặt đúng module và theo convention repository.
- [ ] Không có duplicate code hoặc abstraction không cần thiết.

### Frontend

- [ ] Props, emits, API response và store state có type rõ ràng.
- [ ] Component không gọi HTTP rải rác hoặc chứa business rule nhạy cảm.
- [ ] Có loading, empty, error, forbidden và reconnect state khi liên quan.
- [ ] Form có label, validation, focus và thao tác bàn phím cơ bản.
- [ ] Action hiển thị đúng quyền nhưng backend vẫn kiểm tra lại.

### Backend/API

- [ ] Controller/endpoint mỏng; nghiệp vụ nằm đúng service/use case/domain.
- [ ] DTO tách khỏi entity và không lộ field nội bộ.
- [ ] Validation, authorization theo resource và state transition đầy đủ.
- [ ] Async I/O truyền `CancellationToken`; không có sync-over-async.
- [ ] Query không N+1, có phân trang và order ổn định khi là danh sách.
- [ ] Error code/status và REST contract nhất quán.

### Data, realtime và AI

- [ ] Migration an toàn, không sửa migration đã áp dụng.
- [ ] Event có envelope, scope quyền và xử lý duplicate/reconnect.
- [ ] Job có idempotency, timeout, retry có giới hạn và correlation ID.
- [ ] AI output có version/source và không ghi đè chỉnh sửa người dùng.
- [ ] Không log hoặc trả ra dữ liệu nhạy cảm.

### Xác minh

- [ ] Formatter, lint và type-check chạy thành công.
- [ ] Unit/integration/component test liên quan chạy thành công.
- [ ] Build production chạy thành công.
- [ ] Diff chỉ chứa thay đổi thuộc phạm vi task.
- [ ] Tài liệu/config/example được cập nhật khi cần.
- [ ] Báo cáo rõ lệnh đã chạy, kết quả và rủi ro còn lại.

---

## 20. Mẫu chỉ dẫn bắt buộc cho AI Agent

Khi giao task, có thể dùng đoạn sau:

```text
Trước khi code, hãy đọc toàn bộ WORKFLOW.md và RULES.md, sau đó đọc các chương
đặc tả liên quan. Khảo sát convention và code tương tự trong repository trước
khi tạo hoặc sửa file. Hãy lập kế hoạch ngắn, triển khai đúng phạm vi, giữ nguyên
thay đổi của người dùng, kiểm tra authorization/validation, viết test tương xứng
và chạy formatter, lint, type-check, test, build liên quan. Không được tự ý thay
đổi contract, schema, dependency hoặc kiến trúc. Khi hoàn thành, báo cáo file đã
đổi, lệnh kiểm tra, kết quả, giả định và rủi ro còn lại.
```

---

## 21. Kết luận

Mục tiêu của bộ luật không phải làm code dài hoặc nhiều lớp hơn, mà giúp mỗi thay đổi có tên rõ ràng, trách nhiệm đúng chỗ, contract ổn định, quyền an toàn và khả năng kiểm chứng. Khi chưa có quy định cụ thể, hãy ưu tiên giải pháp đơn giản nhất đáp ứng đúng nghiệp vụ, phù hợp codebase hiện tại và dễ đảo ngược.
