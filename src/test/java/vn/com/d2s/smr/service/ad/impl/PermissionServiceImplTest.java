package vn.com.d2s.smr.service.ad.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionDocument;
import vn.com.d2s.smr.dto.ad.permission.PermissionEffect;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock private AdAccountRepository accountRepository;
    @Mock private MdTitleRepository titleRepository;
    @Mock private MdOrganizeRepository organizeRepository;
    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionServiceImpl(
                accountRepository,
                titleRepository,
                organizeRepository,
                new ObjectMapper()
        );
    }

    @Test
    void resolvesUserThenTitleThenOrganizationPermissionPrecedence() {
        AdAccount account = account();
        account.setPermissionJson("{\"version\":1,\"permissions\":{\"HR_VIEW\":\"Deny\"}}");
        MdTitle title = title();
        title.setPermissionJson("{\"version\":1,\"permissions\":{\"HR_VIEW\":\"Allow\",\"MEETING_CREATE\":\"Allow\"}}");
        MdOrganize organization = organization();
        organization.setPermissionJson("{\"version\":1,\"permissions\":{\"FILE_UPLOAD\":\"Allow\"}}");
        stubHierarchy(account, title, organization);

        List<EffectivePermissionResponse> result = service.getEffectivePermissions("admin");

        EffectivePermissionResponse hrView = permission(result, "HR_VIEW");
        assertThat(hrView.allowed()).isFalse();
        assertThat(hrView.sourceType()).isEqualTo("USER");
        assertThat(permission(result, "MEETING_CREATE").sourceType()).isEqualTo("TITLE");
        assertThat(permission(result, "FILE_UPLOAD").sourceType()).isEqualTo("ORGANIZATION");
        assertThat(permission(result, "AI_SUMMARY").sourceType()).isEqualTo("DEFAULT");
    }

    @Test
    void superAdminReceivesEveryPermission() {
        AdAccount account = account();
        account.setRoleCodes("[\"SUPER_ADMIN\"]");
        stubHierarchy(account, title(), organization());

        List<EffectivePermissionResponse> result = service.getEffectivePermissions("admin");

        assertThat(result).hasSize(26).allMatch(EffectivePermissionResponse::allowed);
        assertThat(result).allMatch(permission -> "ROLE".equals(permission.sourceType()));
    }

    @Test
    void invalidPermissionJsonFailsClosed() {
        AdAccount account = account();
        account.setPermissionJson("not-json");
        stubHierarchy(account, title(), organization());

        EffectivePermissionResponse result = permission(
                service.getEffectivePermissions("admin"),
                "HR_VIEW"
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.sourceType()).isEqualTo("INVALID_JSON");
    }

    @Test
    void serializerNormalizesCodesAndRejectsUnknownCodes() {
        String json = service.serializeAndValidate(new PermissionDocument(
                1,
                Map.of("hr_view", PermissionEffect.ALLOW)
        ));

        assertThat(json).contains("HR_VIEW").contains("Allow");
        assertThatThrownBy(() -> service.serializeAndValidate(new PermissionDocument(
                1,
                Map.of("UNKNOWN", PermissionEffect.ALLOW)
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    private void stubHierarchy(AdAccount account, MdTitle title, MdOrganize organization) {
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        when(titleRepository.findById("STAFF")).thenReturn(Optional.of(title));
        when(organizeRepository.findById("ORG")).thenReturn(Optional.of(organization));
    }

    private static EffectivePermissionResponse permission(
            List<EffectivePermissionResponse> permissions,
            String code
    ) {
        return permissions.stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    private static AdAccount account() {
        AdAccount account = new AdAccount();
        account.setUserName("admin");
        account.setFullName("Admin");
        account.setOrgId("ORG");
        account.setTitleCode("STAFF");
        account.setActive(true);
        return account;
    }

    private static MdTitle title() {
        MdTitle title = new MdTitle();
        title.setCode("STAFF");
        title.setName("Nhân viên");
        return title;
    }

    private static MdOrganize organization() {
        MdOrganize organization = new MdOrganize();
        organization.setId("ORG");
        organization.setName("Công ty");
        return organization;
    }
}
