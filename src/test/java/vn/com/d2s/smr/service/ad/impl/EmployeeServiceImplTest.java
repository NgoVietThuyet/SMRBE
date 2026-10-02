package vn.com.d2s.smr.service.ad.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;
import vn.com.d2s.smr.service.ad.PermissionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private AdAccountRepository adAccountRepository;

    @Mock
    private MdOrganizeRepository mdOrganizeRepository;

    @Mock
    private MdTitleRepository mdTitleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PermissionService permissionService;

    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeServiceImpl(
                adAccountRepository,
                mdOrganizeRepository,
                mdTitleRepository,
                passwordEncoder,
                permissionService
        );
    }

    @Test
    void searchEmployeesReturnsPagedResult() {
        EmployeeItemResponse item = new EmployeeItemResponse(
                "john", "John Doe", "john@example.com", "0123456789", "Hanoi",
                "org1", "Ban Giám đốc", "DEV", "Lập trình viên",
                true, false, null, LocalDateTime.now()
        );
        when(adAccountRepository.findEmployees(eq("org1"), eq("DEV"), eq(true), eq("john"), any(Pageable.class)))
                .thenReturn(List.of(item));
        when(adAccountRepository.countEmployees("org1", "DEV", true, "john")).thenReturn(1L);

        PagedResultResponse<EmployeeItemResponse> res = service.searchEmployees("org1", "DEV", true, "john", 1, 20);

        assertThat(res.items()).hasSize(1);
        assertThat(res.totalItems()).isEqualTo(1);
        assertThat(res.totalPages()).isEqualTo(1);
        assertThat(res.items().get(0).userName()).isEqualTo("john");
    }

    @Test
    void getEmployeeReturnsDetail() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setFullName("John Doe");
        account.setEmail("john@example.com");
        account.setOrgId("org1");
        account.setTitleCode("DEV");
        account.setActive(true);

        MdOrganize org = new MdOrganize();
        org.setId("org1");
        org.setName("Ban Giám đốc");

        MdTitle title = new MdTitle();
        title.setCode("DEV");
        title.setName("Lập trình viên");

        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));
        when(mdOrganizeRepository.findById("org1")).thenReturn(Optional.of(org));
        when(mdTitleRepository.findById("DEV")).thenReturn(Optional.of(title));

        EmployeeDetailResponse res = service.getEmployee("john");

        assertThat(res.userName()).isEqualTo("john");
        assertThat(res.organizationName()).isEqualTo("Ban Giám đốc");
        assertThat(res.titleName()).isEqualTo("Lập trình viên");
    }

    @Test
    void getEmployeeThrowsWhenNotFound() {
        when(adAccountRepository.findById("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEmployee("invalid"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Không tìm thấy tài khoản.");
    }

    @Test
    void createEmployeeSucceeds() {
        EmployeeRequest req = new EmployeeRequest(
                "john", "John Doe", "john@example.com", "0123456789", "Hanoi", "org1", "DEV"
        );
        when(mdOrganizeRepository.existsByIdAndActiveTrue("org1")).thenReturn(true);
        when(mdTitleRepository.existsByCodeAndActiveTrue("DEV")).thenReturn(true);
        when(adAccountRepository.existsByUserNameOrEmail("john", "john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        CreatedEmployeeResponse res = service.createEmployee(req, "admin");

        assertThat(res.userName()).isEqualTo("john");
        assertThat(res.mustChangePassword()).isTrue();
        verify(adAccountRepository).save(any(AdAccount.class));
    }

    @Test
    void createEmployeeThrowsOnDuplicate() {
        EmployeeRequest req = new EmployeeRequest(
                "john", "John Doe", "john@example.com", "0123456789", "Hanoi", "org1", "DEV"
        );
        when(mdOrganizeRepository.existsByIdAndActiveTrue("org1")).thenReturn(true);
        when(mdTitleRepository.existsByCodeAndActiveTrue("DEV")).thenReturn(true);
        when(adAccountRepository.existsByUserNameOrEmail("john", "john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(req, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username hoặc email đã tồn tại.");
    }

    @Test
    void createDirectoryEmployeeNormalizesNameAndHandlesCollision() {
        CreateDirectoryEmployeeRequest req = new CreateDirectoryEmployeeRequest(
                "Nguyen Van A", "a.nguyen@example.com", "0123456789", "Hanoi", "org1", "DEV"
        );
        when(mdOrganizeRepository.existsByIdAndActiveTrue("org1")).thenReturn(true);
        when(mdTitleRepository.existsByCodeAndActiveTrue("DEV")).thenReturn(true);
        when(adAccountRepository.existsByEmail("a.nguyen@example.com")).thenReturn(false);
        when(adAccountRepository.existsById("a")).thenReturn(true);
        when(adAccountRepository.existsById("a01")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        CreatedDirectoryEmployeeResponse res = service.createDirectoryEmployee(req, "admin");

        assertThat(res.userName()).isEqualTo("a01");
        verify(adAccountRepository).save(any(AdAccount.class));
    }

    @Test
    void updateEmployeeSucceedsAndIncrementsTokenVersion() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setTokenVersion(1);

        UpdateEmployeeRequest req = new UpdateEmployeeRequest(
                "John Updated", "john.new@example.com", "0987654321", "HCM", "org1", "DEV"
        );
        when(mdOrganizeRepository.existsByIdAndActiveTrue("org1")).thenReturn(true);
        when(mdTitleRepository.existsByCodeAndActiveTrue("DEV")).thenReturn(true);
        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));
        when(adAccountRepository.existsByEmailAndUserNameNot("john.new@example.com", "john")).thenReturn(false);

        service.updateEmployee("john", req, "admin");

        assertThat(account.getFullName()).isEqualTo("John Updated");
        assertThat(account.getTokenVersion()).isEqualTo(2);
        verify(adAccountRepository).save(account);
    }

    @Test
    void transferEmployeeUpdatesOrgAndIncrementsTokenVersion() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setOrgId("org1");
        account.setTokenVersion(1);

        TransferEmployeeRequest req = new TransferEmployeeRequest("org2", "Transfer reason", null);
        when(mdOrganizeRepository.existsByIdAndActiveTrue("org2")).thenReturn(true);
        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));

        service.transferEmployee("john", req, "admin");

        assertThat(account.getOrgId()).isEqualTo("org2");
        assertThat(account.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void changeEmployeeTitleUpdatesTitleAndIncrementsTokenVersion() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setTitleCode("DEV");
        account.setTokenVersion(1);

        ChangeTitleRequest req = new ChangeTitleRequest("LEAD");
        when(mdTitleRepository.existsByCodeAndActiveTrue("LEAD")).thenReturn(true);
        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));

        service.changeEmployeeTitle("john", req, "admin");

        assertThat(account.getTitleCode()).isEqualTo("LEAD");
        assertThat(account.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void changeEmployeeStatusPreventsSelfLock() {
        ChangeStatusRequest req = new ChangeStatusRequest(false, "Self lock attempt");

        assertThatThrownBy(() -> service.changeEmployeeStatus("admin", req, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Không thể tự khóa tài khoản đang đăng nhập.");
    }

    @Test
    void changeEmployeeStatusLocksAccountAndIncrementsTokenVersion() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setActive(true);
        account.setTokenVersion(1);

        ChangeStatusRequest req = new ChangeStatusRequest(false, "Lock reason");
        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));

        service.changeEmployeeStatus("john", req, "admin");

        assertThat(account.isActive()).isFalse();
        assertThat(account.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void resetEmployeePasswordGeneratesNewPasswordAndIncrementsTokenVersion() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setTokenVersion(1);

        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPassword");

        ResetEmployeePasswordResponse res = service.resetEmployeePassword("john", "admin");

        assertThat(res.userName()).isEqualTo("john");
        assertThat(res.temporaryPassword()).isNotBlank();
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(account.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void getPermissionParsesJsonForAccount() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setPermissionJson("{\"version\":1,\"permissions\":{}}");

        PermissionDocument doc = new PermissionDocument(1, Map.of());
        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));
        when(permissionService.parseAndValidate("{\"version\":1,\"permissions\":{}}")).thenReturn(doc);

        PermissionDocument res = service.getPermission("employee", "john");

        assertThat(res.version()).isEqualTo(1);
    }

    @Test
    void savePermissionUpdatesEmployeePermissionAndTokenVersion() {
        AdAccount account = new AdAccount();
        account.setUserName("john");
        account.setTokenVersion(1);

        PermissionUpdateRequest req = new PermissionUpdateRequest(1, Map.of("HR_VIEW", PermissionEffect.ALLOW));
        when(permissionService.serializeAndValidate(any())).thenReturn("{\"version\":1,\"permissions\":{\"HR_VIEW\":\"Allow\"}}");
        when(adAccountRepository.findById("john")).thenReturn(Optional.of(account));

        service.savePermission("employee", "john", req, "admin");

        assertThat(account.getPermissionJson()).isEqualTo("{\"version\":1,\"permissions\":{\"HR_VIEW\":\"Allow\"}}");
        assertThat(account.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void getEffectivePermissionsDelegatesToPermissionService() {
        EffectivePermissionResponse eff = new EffectivePermissionResponse("HR_VIEW", "Xem nhân sự", true, "Allow", "employee", "john", "John Doe");
        when(adAccountRepository.existsById("john")).thenReturn(true);
        when(permissionService.getEffectivePermissions("john")).thenReturn(List.of(eff));

        List<EffectivePermissionResponse> list = service.getEffectivePermissions("john");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).code()).isEqualTo("HR_VIEW");
    }

    @Test
    void normalizeUserNameStripsAccentsAndSpecialCharacters() {
        assertThat(EmployeeServiceImpl.normalizeUserName("Trần Văn Đạt")).isEqualTo("dat");
        assertThat(EmployeeServiceImpl.normalizeUserName("Lê Thị Bích")).isEqualTo("bich");
        assertThat(EmployeeServiceImpl.normalizeUserName("John Doe")).isEqualTo("doe");
        assertThat(EmployeeServiceImpl.normalizeUserName("")).isEqualTo("user");
    }
}
