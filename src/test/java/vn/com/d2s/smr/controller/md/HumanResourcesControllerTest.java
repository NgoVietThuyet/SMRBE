package vn.com.d2s.smr.controller.md;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.com.d2s.smr.controller.common.GlobalExceptionHandler;
import vn.com.d2s.smr.dto.ad.employee.ChangeStatusRequest;
import vn.com.d2s.smr.dto.ad.employee.ChangeTitleRequest;
import vn.com.d2s.smr.dto.ad.employee.CreateDirectoryEmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.CreatedDirectoryEmployeeResponse;
import vn.com.d2s.smr.dto.ad.employee.CreatedEmployeeResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeDetailResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeItemResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.ResetEmployeePasswordResponse;
import vn.com.d2s.smr.dto.ad.employee.TransferEmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.UpdateEmployeeRequest;
import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionDocument;
import vn.com.d2s.smr.dto.ad.permission.PermissionEffect;
import vn.com.d2s.smr.dto.ad.permission.PermissionUpdateRequest;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.md.hr.HumanResourceSummaryResponse;
import vn.com.d2s.smr.dto.md.hr.MoveOrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrgEmployeeCountResponse;
import vn.com.d2s.smr.dto.md.hr.OrganizationNodeResponse;
import vn.com.d2s.smr.dto.md.hr.OrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrganizationResponse;
import vn.com.d2s.smr.dto.md.hr.TitleDetailResponse;
import vn.com.d2s.smr.dto.md.hr.TitleRequest;
import vn.com.d2s.smr.dto.md.hr.TitleResponse;
import vn.com.d2s.smr.service.ad.EmployeeService;
import vn.com.d2s.smr.service.md.HumanResourceMasterDataService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HumanResourcesControllerTest {

    @Mock
    private HumanResourceMasterDataService hrMasterDataService;

    @Mock
    private EmployeeService employeeService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new HumanResourcesController(hrMasterDataService, employeeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void summaryReturns200WithCorrectJsonShape() throws Exception {
        when(hrMasterDataService.summary()).thenReturn(new HumanResourceSummaryResponse(
                3, 10, 8, 2,
                List.of(new OrgEmployeeCountResponse("org1", "Ban Giám đốc", 3L))
        ));

        mockMvc.perform(get("/api/human-resources/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationCount").value(3))
                .andExpect(jsonPath("$.employeeCount").value(10))
                .andExpect(jsonPath("$.activeAccountCount").value(8))
                .andExpect(jsonPath("$.lockedAccountCount").value(2))
                .andExpect(jsonPath("$.employeesByOrganization[0].id").value("org1"))
                .andExpect(jsonPath("$.employeesByOrganization[0].name").value("Ban Giám đốc"))
                .andExpect(jsonPath("$.employeesByOrganization[0].count").value(3));
    }

    @Test
    void organizationTreeReturns200WithPIdField() throws Exception {
        when(hrMasterDataService.getOrganizationTree()).thenReturn(List.of(
                new OrganizationNodeResponse("org1", "", "Phòng IT", 1, true, true, "Notes", 5)
        ));

        mockMvc.perform(get("/api/human-resources/organization-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("org1"))
                .andExpect(jsonPath("$[0].pId").value(""))
                .andExpect(jsonPath("$[0].name").value("Phòng IT"))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].employeeCount").value(5));
    }

    @Test
    void createOrgReturns200() throws Exception {
        OrganizationRequest req = new OrganizationRequest("Phòng Kế toán", "", 1, true, "Ghi chú");
        OrganizationResponse resp = new OrganizationResponse(
                "org2", "", "Phòng Kế toán", 1, true, null, true, "Ghi chú",
                "admin", LocalDateTime.now(), "admin", LocalDateTime.now()
        );
        when(hrMasterDataService.createOrganization(any(OrganizationRequest.class), eq("admin"))).thenReturn(resp);

        mockMvc.perform(post("/api/human-resources/organizations")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("org2"))
                .andExpect(jsonPath("$.name").value("Phòng Kế toán"));
    }

    @Test
    void updateOrgReturns204NoContent() throws Exception {
        OrganizationRequest req = new OrganizationRequest("Phòng Kế toán Đã Sửa", "", 1, true, "Ghi chú");

        mockMvc.perform(put("/api/human-resources/organizations/org2")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(hrMasterDataService).updateOrganization(eq("org2"), any(OrganizationRequest.class), eq("admin"));
    }

    @Test
    void moveOrgReturns204NoContent() throws Exception {
        MoveOrganizationRequest req = new MoveOrganizationRequest("parent1", 3);

        mockMvc.perform(put("/api/human-resources/organizations/org2/move")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(hrMasterDataService).moveOrganization(eq("org2"), any(MoveOrganizationRequest.class), eq("admin"));
    }

    @Test
    void deleteOrgReturns204NoContent() throws Exception {
        mockMvc.perform(delete("/api/human-resources/organizations/org2"))
                .andExpect(status().isNoContent());

        verify(hrMasterDataService).deleteOrganization("org2");
    }

    @Test
    void titlesReturns200() throws Exception {
        when(hrMasterDataService.getTitles()).thenReturn(List.of(
                new TitleResponse("DEV", "Lập trình viên", "Notes", 1, true, 4)
        ));

        mockMvc.perform(get("/api/human-resources/titles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("DEV"))
                .andExpect(jsonPath("$[0].name").value("Lập trình viên"))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].employeeCount").value(4));
    }

    @Test
    void createTitleReturns200() throws Exception {
        TitleRequest req = new TitleRequest("DEV", "Lập trình viên", "Notes", 1, true);
        TitleDetailResponse resp = new TitleDetailResponse(
                "DEV", "Lập trình viên", "Notes", 1, null, true,
                "admin", LocalDateTime.now(), "admin", LocalDateTime.now()
        );
        when(hrMasterDataService.createTitle(any(TitleRequest.class), eq("admin"))).thenReturn(resp);

        mockMvc.perform(post("/api/human-resources/titles")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DEV"));
    }

    @Test
    void updateTitleReturns204NoContent() throws Exception {
        TitleRequest req = new TitleRequest("DEV", "Lập trình viên Sửa", "Notes", 1, true);

        mockMvc.perform(put("/api/human-resources/titles/DEV")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(hrMasterDataService).updateTitle(eq("DEV"), any(TitleRequest.class), eq("admin"));
    }

    @Test
    void deleteTitleReturns204NoContent() throws Exception {
        mockMvc.perform(delete("/api/human-resources/titles/DEV"))
                .andExpect(status().isNoContent());

        verify(hrMasterDataService).deleteTitle("DEV");
    }

    @Test
    void deleteOrgReturns409OnIllegalState() throws Exception {
        doThrow(new IllegalStateException("Phòng ban còn đơn vị con."))
                .when(hrMasterDataService).deleteOrganization("org1");

        mockMvc.perform(delete("/api/human-resources/organizations/org1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Phòng ban còn đơn vị con."));
    }

    @Test
    void deleteOrgReturns404OnNotFound() throws Exception {
        doThrow(new NoSuchElementException("Không tìm thấy phòng ban."))
                .when(hrMasterDataService).deleteOrganization("invalid");

        mockMvc.perform(delete("/api/human-resources/organizations/invalid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy phòng ban."));
    }

    // --- Wave 4 Employee Controller Tests ---

    @Test
    void searchEmployeesReturns200WithPagedResult() throws Exception {
        EmployeeItemResponse item = new EmployeeItemResponse(
                "john", "John Doe", "john@example.com", "0123456789", "Hanoi",
                "org1", "Ban Giám đốc", "DEV", "Lập trình viên",
                true, false, null, LocalDateTime.now()
        );
        PagedResultResponse<EmployeeItemResponse> paged = new PagedResultResponse<>(
                List.of(item), 1, 20, 1, 1
        );
        when(employeeService.searchEmployees(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(paged);

        mockMvc.perform(get("/api/human-resources/employees")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userName").value("john"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getEmployeeReturns200() throws Exception {
        EmployeeDetailResponse detail = new EmployeeDetailResponse(
                "john", "John Doe", "john@example.com", "0123456789", "Hanoi",
                "org1", "Ban Giám đốc", "DEV", "Lập trình viên",
                true, false, null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(employeeService.getEmployee("john")).thenReturn(detail);

        mockMvc.perform(get("/api/human-resources/employees/john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("john"))
                .andExpect(jsonPath("$.organizationName").value("Ban Giám đốc"));
    }

    @Test
    void createEmployeeReturns200() throws Exception {
        EmployeeRequest req = new EmployeeRequest("john", "John Doe", "john@example.com", "0123456789", "Hanoi", "org1", "DEV");
        CreatedEmployeeResponse resp = new CreatedEmployeeResponse("john", "John Doe", "john@example.com", "TempPass123!", true);
        when(employeeService.createEmployee(any(EmployeeRequest.class), eq("admin"))).thenReturn(resp);

        mockMvc.perform(post("/api/human-resources/employees")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("john"))
                .andExpect(jsonPath("$.temporaryPassword").value("TempPass123!"));
    }

    @Test
    void createDirectoryEmployeeReturns200() throws Exception {
        CreateDirectoryEmployeeRequest req = new CreateDirectoryEmployeeRequest("Nguyen Van A", "a.nguyen@example.com", "0123456789", "Hanoi", "org1", "DEV");
        CreatedDirectoryEmployeeResponse resp = new CreatedDirectoryEmployeeResponse("a");
        when(employeeService.createDirectoryEmployee(any(CreateDirectoryEmployeeRequest.class), eq("admin"))).thenReturn(resp);

        mockMvc.perform(post("/api/human-resources/directory-employees")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("a"));
    }

    @Test
    void updateEmployeeReturns204NoContent() throws Exception {
        UpdateEmployeeRequest req = new UpdateEmployeeRequest("John Updated", "john@example.com", "0123456789", "Hanoi", "org1", "DEV");

        mockMvc.perform(put("/api/human-resources/employees/john")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(employeeService).updateEmployee(eq("john"), any(UpdateEmployeeRequest.class), eq("admin"));
    }

    @Test
    void transferEmployeeReturns204NoContent() throws Exception {
        TransferEmployeeRequest req = new TransferEmployeeRequest("org2", null, null);

        mockMvc.perform(put("/api/human-resources/employees/john/organization")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(employeeService).transferEmployee(eq("john"), any(TransferEmployeeRequest.class), eq("admin"));
    }

    @Test
    void changeTitleReturns204NoContent() throws Exception {
        ChangeTitleRequest req = new ChangeTitleRequest("LEAD");

        mockMvc.perform(put("/api/human-resources/employees/john/title")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(employeeService).changeEmployeeTitle(eq("john"), any(ChangeTitleRequest.class), eq("admin"));
    }

    @Test
    void changeStatusReturns204NoContent() throws Exception {
        ChangeStatusRequest req = new ChangeStatusRequest(false, null);

        mockMvc.perform(put("/api/human-resources/employees/john/status")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(employeeService).changeEmployeeStatus(eq("john"), any(ChangeStatusRequest.class), eq("admin"));
    }

    @Test
    void resetPasswordReturns200() throws Exception {
        ResetEmployeePasswordResponse resp = new ResetEmployeePasswordResponse("john", "Temp123!");
        when(employeeService.resetEmployeePassword("john", "admin")).thenReturn(resp);

        mockMvc.perform(post("/api/human-resources/employees/john/reset-password")
                        .principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("john"))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp123!"));
    }

    @Test
    void getPermissionReturns200() throws Exception {
        PermissionDocument doc = new PermissionDocument(1, Map.of());
        when(employeeService.getPermission("employee", "john")).thenReturn(doc);

        mockMvc.perform(get("/api/human-resources/employee/john/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void savePermissionReturns204NoContent() throws Exception {
        PermissionUpdateRequest req = new PermissionUpdateRequest(1, Map.of("HR_VIEW", PermissionEffect.ALLOW));

        mockMvc.perform(put("/api/human-resources/employee/john/permissions")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(employeeService).savePermission(eq("employee"), eq("john"), any(PermissionUpdateRequest.class), eq("admin"));
    }

    @Test
    void getEffectivePermissionsReturns200() throws Exception {
        EffectivePermissionResponse eff = new EffectivePermissionResponse("HR_VIEW", "Xem nhân sự", true, "Allow", "employee", "john", "John Doe");
        when(employeeService.getEffectivePermissions("john")).thenReturn(List.of(eff));

        mockMvc.perform(get("/api/human-resources/employees/john/effective-permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("HR_VIEW"));
    }
}
