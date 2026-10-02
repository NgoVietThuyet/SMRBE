package vn.com.d2s.smr.service.md.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.com.d2s.smr.service.md.HumanResourceMasterDataService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class HumanResourceMasterDataServiceImpl implements HumanResourceMasterDataService {

    private final MdOrganizeRepository mdOrganizeRepository;
    private final MdTitleRepository mdTitleRepository;
    private final AdAccountRepository adAccountRepository;

    public HumanResourceMasterDataServiceImpl(
            MdOrganizeRepository mdOrganizeRepository,
            MdTitleRepository mdTitleRepository,
            AdAccountRepository adAccountRepository
    ) {
        this.mdOrganizeRepository = mdOrganizeRepository;
        this.mdTitleRepository = mdTitleRepository;
        this.adAccountRepository = adAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public HumanResourceSummaryResponse summary() {
        long orgs = mdOrganizeRepository.count();
        long employees = adAccountRepository.count();
        long active = adAccountRepository.countByActive(true);
        List<OrgEmployeeCountResponse> byOrg = mdOrganizeRepository.getOrgEmployeeCounts();
        return new HumanResourceSummaryResponse(
                (int) orgs,
                (int) employees,
                (int) active,
                (int) (employees - active),
                byOrg
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationNodeResponse> getOrganizationTree() {
        List<MdOrganize> list = mdOrganizeRepository.findAllByOrderByOrderNumberAscNameAsc();
        return list.stream().map(o -> {
            long count = adAccountRepository.countByOrgId(o.getId());
            return new OrganizationNodeResponse(
                    o.getId(),
                    o.getParentId() != null ? o.getParentId() : "",
                    o.getName(),
                    o.getOrderNumber(),
                    o.isExpanded(),
                    o.isActive(),
                    o.getNotes(),
                    count
            );
        }).toList();
    }

    @Override
    @Transactional
    public OrganizationResponse createOrganization(OrganizationRequest r, String actor) {
        if (r.name() == null || r.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên phòng ban không được trống.");
        }
        if (r.parentId() != null && !r.parentId().isEmpty() && !mdOrganizeRepository.existsById(r.parentId())) {
            throw new IllegalArgumentException("Phòng ban cha không tồn tại.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        MdOrganize e = new MdOrganize();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setParentId(r.parentId() != null ? r.parentId() : "");
        e.setName(r.name().trim());
        e.setOrderNumber(r.orderNumber());
        e.setExpanded(true);
        e.setActive(r.active());
        e.setNotes(r.notes());
        e.setCreateBy(actor);
        e.setCreateDate(now);
        e.setUpdateBy(actor);
        e.setUpdateDate(now);

        MdOrganize saved = mdOrganizeRepository.save(e);
        return mapToOrgResponse(saved);
    }

    @Override
    @Transactional
    public void updateOrganization(String id, OrganizationRequest r, String actor) {
        MdOrganize e = mdOrganizeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phòng ban."));
        String newParentId = r.parentId() != null ? r.parentId() : "";
        if (id.equals(newParentId) || isDescendant(id, newParentId)) {
            throw new IllegalStateException("Không thể tạo vòng lặp cây tổ chức.");
        }
        if (!newParentId.isEmpty() && !mdOrganizeRepository.existsById(newParentId)) {
            throw new IllegalArgumentException("Phòng ban cha không tồn tại.");
        }
        if (r.name() == null || r.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên phòng ban không được trống.");
        }
        e.setParentId(newParentId);
        e.setName(r.name().trim());
        e.setOrderNumber(r.orderNumber());
        e.setActive(r.active());
        e.setNotes(r.notes());
        e.setUpdateBy(actor);
        e.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));
        mdOrganizeRepository.save(e);
    }

    @Override
    @Transactional
    public void moveOrganization(String id, MoveOrganizationRequest r, String actor) {
        MdOrganize e = mdOrganizeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phòng ban."));
        String newParentId = r.parentId() != null ? r.parentId() : "";
        if (id.equals(newParentId) || isDescendant(id, newParentId)) {
            throw new IllegalStateException("Không thể chuyển phòng ban vào cây con của chính nó.");
        }
        if (!newParentId.isEmpty() && !mdOrganizeRepository.existsById(newParentId)) {
            throw new IllegalArgumentException("Phòng ban cha không tồn tại.");
        }
        e.setParentId(newParentId);
        e.setOrderNumber(r.orderNumber());
        e.setUpdateBy(actor);
        e.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));
        mdOrganizeRepository.save(e);
    }

    @Override
    @Transactional
    public void deleteOrganization(String id) {
        if (mdOrganizeRepository.existsByParentId(id)) {
            throw new IllegalStateException("Phòng ban còn đơn vị con.");
        }
        if (adAccountRepository.existsByOrgId(id)) {
            throw new IllegalStateException("Phòng ban đang có nhân sự.");
        }
        MdOrganize e = mdOrganizeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phòng ban."));
        mdOrganizeRepository.delete(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TitleResponse> getTitles() {
        List<MdTitle> list = mdTitleRepository.findAllByOrderByOrderNumberAsc();
        return list.stream().map(t -> new TitleResponse(
                t.getCode(),
                t.getName(),
                t.getNotes() != null ? t.getNotes() : "",
                t.getOrderNumber(),
                t.isActive(),
                adAccountRepository.countByTitleCode(t.getCode())
        )).toList();
    }

    @Override
    @Transactional
    public TitleDetailResponse createTitle(TitleRequest r, String actor) {
        if (r.code() == null || r.code().trim().isEmpty()) {
            throw new IllegalArgumentException("Mã chức danh không được trống.");
        }
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        if (mdTitleRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã chức danh đã tồn tại.");
        }
        if (r.name() == null || r.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên chức danh không được trống.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        MdTitle t = new MdTitle();
        t.setCode(code);
        t.setName(r.name().trim());
        t.setNotes(r.notes() != null ? r.notes() : "");
        t.setOrderNumber(r.orderNumber());
        t.setActive(r.active());
        t.setCreateBy(actor);
        t.setCreateDate(now);
        t.setUpdateBy(actor);
        t.setUpdateDate(now);

        MdTitle saved = mdTitleRepository.save(t);
        return mapToTitleResponse(saved);
    }

    @Override
    @Transactional
    public void updateTitle(String code, TitleRequest r, String actor) {
        MdTitle t = mdTitleRepository.findById(code)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chức danh."));
        if (r.name() == null || r.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên chức danh không được trống.");
        }
        t.setName(r.name().trim());
        t.setNotes(r.notes() != null ? r.notes() : "");
        t.setOrderNumber(r.orderNumber());
        t.setActive(r.active());
        t.setUpdateBy(actor);
        t.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));
        mdTitleRepository.save(t);
    }

    @Override
    @Transactional
    public void deleteTitle(String code) {
        if (adAccountRepository.existsByTitleCode(code)) {
            throw new IllegalStateException("Chức danh đang được sử dụng.");
        }
        MdTitle t = mdTitleRepository.findById(code)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chức danh."));
        mdTitleRepository.delete(t);
    }

    private boolean isDescendant(String rootId, String candidateId) {
        String current = candidateId;
        Set<String> seen = new HashSet<>();
        while (current != null && !current.isEmpty() && seen.add(current)) {
            if (current.equals(rootId)) {
                return true;
            }
            Optional<MdOrganize> orgOpt = mdOrganizeRepository.findById(current);
            if (orgOpt.isEmpty()) {
                break;
            }
            current = orgOpt.get().getParentId();
        }
        return false;
    }

    private static OrganizationResponse mapToOrgResponse(MdOrganize o) {
        return new OrganizationResponse(
                o.getId(),
                o.getParentId(),
                o.getName(),
                o.getOrderNumber(),
                o.isExpanded(),
                o.getPermissionJson(),
                o.isActive(),
                o.getNotes(),
                o.getCreateBy(),
                o.getCreateDate(),
                o.getUpdateBy(),
                o.getUpdateDate()
        );
    }

    private static TitleDetailResponse mapToTitleResponse(MdTitle t) {
        return new TitleDetailResponse(
                t.getCode(),
                t.getName(),
                t.getNotes(),
                t.getOrderNumber(),
                t.getPermissionJson(),
                t.isActive(),
                t.getCreateBy(),
                t.getCreateDate(),
                t.getUpdateBy(),
                t.getUpdateDate()
        );
    }
}
