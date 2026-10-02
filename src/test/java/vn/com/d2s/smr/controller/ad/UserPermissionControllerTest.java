package vn.com.d2s.smr.controller.ad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.user.UserSearchResponse;
import vn.com.d2s.smr.service.ad.PermissionService;
import vn.com.d2s.smr.service.ad.UserService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserPermissionControllerTest {

    @Mock private UserService userService;
    @Mock private PermissionService permissionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UserController(userService),
                new PermissionsController(permissionService)
        ).build();
    }

    @Test
    void searchReturnsRawArrayCompatibleWithFrontend() throws Exception {
        when(userService.search("admin", "an", 20)).thenReturn(List.of(
                new UserSearchResponse("an01", "Nguyễn Văn An", "an@example.com")
        ));

        mockMvc.perform(get("/api/User/Search")
                        .principal(() -> "admin")
                        .queryParam("q", "an")
                        .queryParam("take", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userName").value("an01"))
                .andExpect(jsonPath("$[0].fullName").value("Nguyễn Văn An"));

        verify(userService).search("admin", "an", 20);
    }

    @Test
    void catalogReturnsGroupsInDotNetOrder() throws Exception {
        mockMvc.perform(get("/api/permissions/catalog").principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupName").value("Nhân sự"))
                .andExpect(jsonPath("$[0].permissions[0].code").value("HR_VIEW"));
    }

    @Test
    void meReturnsRawEffectivePermissionArray() throws Exception {
        when(permissionService.getEffectivePermissions("admin")).thenReturn(List.of(
                new EffectivePermissionResponse(
                        "HR_VIEW", "Xem module nhân sự", true, "Allow",
                        "ROLE", "SUPER_ADMIN", "Quản trị hệ thống"
                )
        ));

        mockMvc.perform(get("/api/permissions/me").principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("HR_VIEW"))
                .andExpect(jsonPath("$[0].allowed").value(true))
                .andExpect(jsonPath("$[0].sourceType").value("ROLE"));
    }
}
