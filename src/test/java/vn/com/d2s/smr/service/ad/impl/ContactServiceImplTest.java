package vn.com.d2s.smr.service.ad.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.d2s.smr.dto.ad.contact.ContactResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceImplTest {

    @Mock private AdAccountRepository accountRepository;
    @Mock private MdOrganizeRepository organizeRepository;
    @Mock private MdTitleRepository titleRepository;

    private ContactServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContactServiceImpl(accountRepository, organizeRepository, titleRepository);
        when(organizeRepository.findAll()).thenReturn(List.of(org("ROOT", ""), org("KT", "ROOT"), org("KT1", "KT"), org("NS", "ROOT")));
        MdTitle t = new MdTitle();
        t.setCode("NV");
        t.setName("Nhân viên");
        when(titleRepository.findAll()).thenReturn(List.of(t));
        when(accountRepository.findAll()).thenReturn(List.of(
                acc("an", "Nguyễn Văn Ân", "KT", true),
                acc("dung", "Trần Dũng", "KT1", true),
                acc("binh", "Lê Bình", "NS", true),
                acc("khoa", "Đỗ Khoá", "KT", false)));
    }

    @Test
    void organizationFilterIncludesSubUnitsAndSkipsLockedAccounts() {
        var page = service.search("KT", null, 1, 50);

        assertThat(page.items()).extracting(ContactResponse::userName).containsExactlyInAnyOrder("an", "dung");
        assertThat(page.items().get(0).titleName()).isEqualTo("Nhân viên");
    }

    @Test
    void keywordIgnoresDiacritics() {
        assertThat(service.search(null, "nguyen an", 1, 50).items()).extracting(ContactResponse::userName).containsExactly("an");
        assertThat(service.search(null, "Dũng", 1, 50).items()).extracting(ContactResponse::userName).containsExactly("dung");
        assertThat(service.search(null, "do khoa", 1, 50).items()).isEmpty(); // tài khoản đã khoá không hiện
    }

    @Test
    void pagesAndSortsByGivenName() {
        var first = service.search(null, null, 1, 2);
        assertThat(first.totalItems()).isEqualTo(3);
        // Ân < Bình < Dũng (sắp theo tên)
        assertThat(first.items()).extracting(ContactResponse::userName).containsExactly("an", "binh");
        assertThat(service.search(null, null, 2, 2).items()).extracting(ContactResponse::userName).containsExactly("dung");
    }

    private static MdOrganize org(String id, String parent) {
        MdOrganize o = new MdOrganize();
        o.setId(id);
        o.setParentId(parent);
        o.setName(id);
        return o;
    }

    private static AdAccount acc(String user, String name, String org, boolean active) {
        AdAccount a = new AdAccount();
        a.setUserName(user);
        a.setFullName(name);
        a.setOrgId(org);
        a.setTitleCode("NV");
        a.setActive(active);
        return a;
    }
}
