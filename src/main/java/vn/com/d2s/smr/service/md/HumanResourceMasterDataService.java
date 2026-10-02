package vn.com.d2s.smr.service.md;

import vn.com.d2s.smr.dto.md.hr.HumanResourceSummaryResponse;
import vn.com.d2s.smr.dto.md.hr.MoveOrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrganizationNodeResponse;
import vn.com.d2s.smr.dto.md.hr.OrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrganizationResponse;
import vn.com.d2s.smr.dto.md.hr.TitleDetailResponse;
import vn.com.d2s.smr.dto.md.hr.TitleRequest;
import vn.com.d2s.smr.dto.md.hr.TitleResponse;

import java.util.List;

public interface HumanResourceMasterDataService {

    HumanResourceSummaryResponse summary();

    List<OrganizationNodeResponse> getOrganizationTree();

    OrganizationResponse createOrganization(OrganizationRequest request, String actor);

    void updateOrganization(String id, OrganizationRequest request, String actor);

    void moveOrganization(String id, MoveOrganizationRequest request, String actor);

    void deleteOrganization(String id);

    List<TitleResponse> getTitles();

    TitleDetailResponse createTitle(TitleRequest request, String actor);

    void updateTitle(String code, TitleRequest request, String actor);

    void deleteTitle(String code);
}
