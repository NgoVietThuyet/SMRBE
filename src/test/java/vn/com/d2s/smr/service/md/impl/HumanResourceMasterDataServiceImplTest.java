package vn.com.d2s.smr.service.md.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.d2s.smr.dto.md.hr.HumanResourceSummaryResponse;
import vn.com.d2s.smr.dto.md.hr.MoveOrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrgEmployeeCountResponse;
import vn.com.d2s.smr.dto.md.hr.OrganizationNodeResponse;
import vn.com.d2s.smr.dto.md.hr.OrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrganizationResponse;
import vn.com.d2s.smr.dto.md.hr.TitleDetailResponse;
import vn.com.d2s.smr.dto.md.hr.TitleRequest;
import vn.com.d2s.smr.dto.md.hr.TitleResponse;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HumanResourceMasterDataServiceImplTest {

    @Mock
    private MdOrganizeRepository mdOrganizeRepository;

    @Mock
    private MdTitleRepository mdTitleRepository;

    @Mock
    private AdAccountRepository adAccountRepository;

    private HumanResourceMasterDataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HumanResourceMasterDataServiceImpl(
                mdOrganizeRepository,
                mdTitleRepository,
                adAccountRepository
        );
    }

    @Test
    void summaryReturnsCorrectCountsAndGrouping() {
        when(mdOrganizeRepository.count()).thenReturn(5L);
        when(adAccountRepository.count()).thenReturn(10L);
        when(adAccountRepository.countByActive(true)).thenReturn(8L);
        when(mdOrganizeRepository.getOrgEmployeeCounts()).thenReturn(List.of(
                new OrgEmployeeCountResponse("org1", "Ban Giám đốc", 3L)
        ));

        HumanResourceSummaryResponse res = service.summary();

        assertThat(res.organizationCount()).isEqualTo(5);
        assertThat(res.employeeCount()).isEqualTo(10);
        assertThat(res.activeAccountCount()).isEqualTo(8);
        assertThat(res.lockedAccountCount()).isEqualTo(2);
        assertThat(res.employeesByOrganization()).hasSize(1);
        assertThat(res.employeesByOrganization().get(0).name()).isEqualTo("Ban Giám đốc");
    }

    @Test
    void getOrganizationTreeReturnsNodeResponses() {
        MdOrganize org = new MdOrganize();
        org.setId("org1");
        org.setParentId("");
        org.setName("Phòng IT");
        org.setOrderNumber(1);
        org.setExpanded(true);
        org.setActive(true);
        org.setNotes("Notes");

        when(mdOrganizeRepository.findAllByOrderByOrderNumberAscNameAsc()).thenReturn(List.of(org));
        when(adAccountRepository.countByOrgId("org1")).thenReturn(5L);

        List<OrganizationNodeResponse> tree = service.getOrganizationTree();

        assertThat(tree).hasSize(1);
        OrganizationNodeResponse node = tree.get(0);
        assertThat(node.id()).isEqualTo("org1");
        assertThat(node.name()).isEqualTo("Phòng IT");
        assertThat(node.employeeCount()).isEqualTo(5);
    }

    @Test
    void createOrganizationSucceeds() {
        OrganizationRequest request = new OrganizationRequest("Phòng Kế toán", "", 2, true, "Ghi chú");
        when(mdOrganizeRepository.save(any(MdOrganize.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationResponse response = service.createOrganization(request, "admin");

        assertThat(response.name()).isEqualTo("Phòng Kế toán");
        assertThat(response.orderNumber()).isEqualTo(2);
        assertThat(response.createBy()).isEqualTo("admin");
    }

    @Test
    void createOrganizationThrowsWhenNameBlank() {
        OrganizationRequest request = new OrganizationRequest("   ", "", 1, true, null);

        assertThatThrownBy(() -> service.createOrganization(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tên phòng ban không được trống.");
    }

    @Test
    void createOrganizationThrowsWhenParentNotFound() {
        OrganizationRequest request = new OrganizationRequest("Phòng Con", "invalid_p", 1, true, null);
        when(mdOrganizeRepository.existsById("invalid_p")).thenReturn(false);

        assertThatThrownBy(() -> service.createOrganization(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phòng ban cha không tồn tại.");
    }

    @Test
    void updateOrganizationPreventsSelfParentAndCycle() {
        MdOrganize org = new MdOrganize();
        org.setId("org1");
        org.setParentId("");
        org.setName("Phòng A");

        when(mdOrganizeRepository.findById("org1")).thenReturn(Optional.of(org));

        OrganizationRequest request = new OrganizationRequest("Phòng A", "org1", 1, true, null);

        assertThatThrownBy(() -> service.updateOrganization("org1", request, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Không thể tạo vòng lặp cây tổ chức.");
    }

    @Test
    void moveOrganizationUpdatesParentAndOrderNumber() {
        MdOrganize org = new MdOrganize();
        org.setId("org1");
        org.setParentId("");
        org.setName("Phòng A");

        MdOrganize parentOrg = new MdOrganize();
        parentOrg.setId("parent1");

        when(mdOrganizeRepository.findById("org1")).thenReturn(Optional.of(org));
        when(mdOrganizeRepository.existsById("parent1")).thenReturn(true);

        MoveOrganizationRequest request = new MoveOrganizationRequest("parent1", 5);

        service.moveOrganization("org1", request, "admin");

        assertThat(org.getParentId()).isEqualTo("parent1");
        assertThat(org.getOrderNumber()).isEqualTo(5);
        verify(mdOrganizeRepository).save(org);
    }

    @Test
    void deleteOrganizationThrowsWhenHasChildrenOrEmployees() {
        when(mdOrganizeRepository.existsByParentId("org1")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteOrganization("org1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Phòng ban còn đơn vị con.");
    }

    @Test
    void getTitlesReturnsTitleResponses() {
        MdTitle title = new MdTitle();
        title.setCode("DEV");
        title.setName("Lập trình viên");
        title.setNotes("Developer");
        title.setOrderNumber(1);
        title.setActive(true);

        when(mdTitleRepository.findAllByOrderByOrderNumberAsc()).thenReturn(List.of(title));
        when(adAccountRepository.countByTitleCode("DEV")).thenReturn(4L);

        List<TitleResponse> titles = service.getTitles();

        assertThat(titles).hasSize(1);
        assertThat(titles.get(0).code()).isEqualTo("DEV");
        assertThat(titles.get(0).employeeCount()).isEqualTo(4);
    }

    @Test
    void createTitleNormalizesCodeAndSucceeds() {
        TitleRequest request = new TitleRequest("  dev  ", "Lập trình viên", "Dev notes", 1, true);
        when(mdTitleRepository.existsByCode("DEV")).thenReturn(false);
        when(mdTitleRepository.save(any(MdTitle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TitleDetailResponse res = service.createTitle(request, "admin");

        assertThat(res.code()).isEqualTo("DEV");
        assertThat(res.name()).isEqualTo("Lập trình viên");
    }

    @Test
    void createTitleThrowsOnDuplicateCode() {
        TitleRequest request = new TitleRequest("DEV", "Lập trình viên", null, 1, true);
        when(mdTitleRepository.existsByCode("DEV")).thenReturn(true);

        assertThatThrownBy(() -> service.createTitle(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mã chức danh đã tồn tại.");
    }

    @Test
    void deleteTitleThrowsWhenTitleInUse() {
        when(adAccountRepository.existsByTitleCode("DEV")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteTitle("DEV"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chức danh đang được sử dụng.");
    }

    @Test
    void deleteTitleSucceedsWhenNotInUse() {
        MdTitle title = new MdTitle();
        title.setCode("DEV");
        when(adAccountRepository.existsByTitleCode("DEV")).thenReturn(false);
        when(mdTitleRepository.findById("DEV")).thenReturn(Optional.of(title));

        service.deleteTitle("DEV");

        verify(mdTitleRepository).delete(title);
    }
}
