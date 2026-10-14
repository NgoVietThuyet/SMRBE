package vn.com.d2s.smr.service.ad.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.common.TextSearch;
import vn.com.d2s.smr.dto.ad.contact.ContactResponse;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;
import vn.com.d2s.smr.service.ad.ContactService;

import java.text.Collator;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Danh bạ (P15). Lọc trong bộ nhớ vì cần so khớp bỏ dấu tiếng Việt (collation SQL Server không gộp đ/d);
 * số tài khoản của một đơn vị nhỏ (vài trăm) nên đủ nhanh.
 */
@Service
public class ContactServiceImpl implements ContactService {

    private final AdAccountRepository accountRepository;
    private final MdOrganizeRepository organizeRepository;
    private final MdTitleRepository titleRepository;

    public ContactServiceImpl(AdAccountRepository accountRepository, MdOrganizeRepository organizeRepository,
                              MdTitleRepository titleRepository) {
        this.accountRepository = accountRepository;
        this.organizeRepository = organizeRepository;
        this.titleRepository = titleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResultResponse<ContactResponse> search(String organizationId, String keyword, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.clamp(pageSize, 1, 100);

        List<MdOrganize> orgs = organizeRepository.findAll();
        Map<String, String> orgNames = orgs.stream().collect(Collectors.toMap(MdOrganize::getId, MdOrganize::getName, (a, b) -> a));
        Map<String, String> titleNames = titleRepository.findAll().stream()
                .collect(Collectors.toMap(MdTitle::getCode, MdTitle::getName, (a, b) -> a));
        Set<String> scope = organizationId == null || organizationId.isBlank() ? null : subtree(organizationId.trim(), orgs);

        Collator vi = Collator.getInstance(Locale.forLanguageTag("vi"));
        List<ContactResponse> all = accountRepository.findAll().stream()
                .filter(AdAccount::isActive)
                .filter(a -> scope == null || scope.contains(a.getOrgId()))
                .map(a -> new ContactResponse(a.getUserName(), a.getFullName(), a.getEmail(), a.getPhone(), a.getOrgId(),
                        orgNames.get(a.getOrgId()), a.getTitleCode(), titleNames.get(a.getTitleCode())))
                .filter(c -> TextSearch.matches(keyword, c.fullName(), c.userName(), c.email(), c.phone(), c.titleName()))
                .sorted(Comparator.comparing((ContactResponse c) -> lastName(c.fullName()), vi)
                        .thenComparing(c -> c.fullName() == null ? "" : c.fullName(), vi))
                .toList();

        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PagedResultResponse.of(all.subList(from, to), p, size, all.size());
    }

    /** Phòng ban và mọi phòng ban con. */
    static Set<String> subtree(String rootId, List<MdOrganize> orgs) {
        Map<String, List<String>> children = new HashMap<>();
        orgs.forEach(o -> children.computeIfAbsent(o.getParentId(), k -> new java.util.ArrayList<>()).add(o.getId()));
        Set<String> ids = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (ids.add(id)) {
                children.getOrDefault(id, List.of()).forEach(stack::push);
            }
        }
        return ids;
    }

    /** Sắp theo tên (từ cuối) như danh bạ tiếng Việt. */
    private static String lastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }
}
