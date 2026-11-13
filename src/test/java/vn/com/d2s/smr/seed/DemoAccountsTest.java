package vn.com.d2s.smr.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Quy tắc tài khoản demo (email, mật khẩu, chủ trì, phòng ban) và nội dung demo-data.json, không cần DB. */
class DemoAccountsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DemoData DATA = DemoData.load(MAPPER);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 0);
    private static final Set<String> HOSTS = Set.of("@thuyet", "@duy", "@toan");

    @Test
    void emailAndPasswordFollowUserName() {
        assertThat(DemoAccounts.email("duc.nguyen")).isEqualTo("duc.nguyen@gmail.com");
        assertThat(DemoAccounts.password("duc.nguyen")).isEqualTo("duc.nguyen123");
    }

    @Test
    void normalizeIgnoresDiacriticsAndCase() {
        assertThat(DemoAccounts.normalize("Ngô Viết Thuyết")).isEqualTo("ngo viet thuyet");
        // Lỗi gõ "Ngọ" thay cho "Ngô" vẫn khớp cùng họ tên không dấu.
        assertThat(DemoAccounts.normalize("  Ngọ  Viết Thuyết ")).isEqualTo(DemoAccounts.normalize("Ngô Viết Thuyết"));
        assertThat(DemoAccounts.normalize("Đỗ Quỳnh Nga")).isEqualTo("do quynh nga");
    }

    @Test
    void leafOrgsExcludeParentsAndSystemRoot() {
        List<DemoAccounts.OrgRow> orgs = List.of(
                new DemoAccounts.OrgRow("ORG_SYS", ""), new DemoAccounts.OrgRow("ORG_ROOT", ""),
                new DemoAccounts.OrgRow("ORG_BLOCK", "ORG_ROOT"), new DemoAccounts.OrgRow("ORG_B", "ORG_BLOCK"),
                new DemoAccounts.OrgRow("ORG_A", "ORG_BLOCK"));
        assertThat(DemoAccounts.leafOrgIds(orgs)).containsExactly("ORG_A", "ORG_B");
    }

    @Test
    void titlesExcludeAdmin() {
        assertThat(DemoAccounts.usableTitleCodes(List.of("ADMIN", "DEV", "admin", "QA"))).containsExactly("DEV", "QA");
    }

    @Test
    void assignmentIsStableAndStaysInsideCandidates() {
        List<String> users = DATA.accounts().stream().map(DemoData.Account::userName).toList();
        List<String> orgs = List.of("ORG_A", "ORG_B", "ORG_C");
        Map<String, String> first = DemoAccounts.assign(users, orgs, "org");
        assertThat(first).hasSize(users.size());
        assertThat(first.values()).allMatch(orgs::contains);
        assertThat(DemoAccounts.assign(users, orgs, "org")).isEqualTo(first);
        assertThat(DemoAccounts.assign(users, List.of(), "org")).isEmpty();
    }

    @Test
    void resolvesHostsByFullName() {
        Map<String, String> users = new LinkedHashMap<>();
        users.put("thuyet.ngo", "Ngô Viết Thuyết");
        users.put("duy.vu", "Vũ Bá Duy");
        users.put("toan.hoang", "Hoàng Văn Toàn");
        users.put("other", "Người Khác");
        DemoAccounts.HostResolution r = DemoAccounts.resolveHosts(DATA.hostAccounts(), users);
        assertThat(r.problems()).isEmpty();
        assertThat(r.aliasToUser()).containsEntry("@thuyet", "thuyet.ngo").containsEntry("@duy", "duy.vu").containsEntry("@toan", "toan.hoang");
    }

    @Test
    void reportsMissingAndAmbiguousHosts() {
        Map<String, String> users = new LinkedHashMap<>();
        users.put("duy.vu", "Vũ Bá Duy");
        users.put("duy2", "vu ba duy");
        DemoAccounts.HostResolution r = DemoAccounts.resolveHosts(DATA.hostAccounts(), users);
        assertThat(r.problems()).hasSize(3);
        assertThat(r.problems()).anyMatch(p -> p.contains("Thuyết")).anyMatch(p -> p.contains("duy.vu, duy2")).anyMatch(p -> p.contains("Toàn"));
        assertThat(r.aliasToUser()).isEmpty();
    }

    @Test
    void aliasesAreReplacedWhenLoadingContent() {
        DemoData resolved = DemoData.load(MAPPER, Map.of("@thuyet", "thuyet.ngo", "@duy", "duy.vu", "@toan", "toan.hoang"));
        Set<String> hosts = Set.of("thuyet.ngo", "duy.vu", "toan.hoang");
        assertThat(resolved.meetings()).allMatch(m -> hosts.contains(m.host()));
        assertThat(resolved.recurring()).allMatch(r -> hosts.contains(r.host()));
        assertThat(resolved.toString()).doesNotContain("@thuyet", "@duy", "@toan");
    }

    @Test
    void everyMeetingIsChairedByOneOfThreeHosts() {
        assertThat(DATA.meetings()).allMatch(m -> HOSTS.contains(m.host()));
        assertThat(DATA.recurring()).allMatch(r -> HOSTS.contains(r.host()));
        // Cả ba người đều làm chủ trì (không dồn vào một người).
        assertThat(DATA.meetings().stream().map(DemoData.Meeting::host).distinct()).containsExactlyInAnyOrderElementsOf(HOSTS);
        // Các cuộc họp sinh ra từ lịch định kỳ cũng do 3 người này chủ trì.
        assertThat(DemoPlan.plan(DATA, NOW)).allMatch(p -> HOSTS.contains(p.source().host()));
    }

    @Test
    void onlyTenDemoAccountsAndThreeHostsAreReferenced() {
        Set<String> allowed = new HashSet<>(HOSTS);
        DATA.accounts().forEach(a -> allowed.add(a.userName()));
        assertThat(DATA.accounts()).hasSize(10);
        Set<String> used = new HashSet<>();
        for (DemoPlan.PlannedMeeting p : DemoPlan.plan(DATA, NOW)) used.addAll(p.members().keySet());
        DATA.transcripts().values().forEach(t -> t.forEach(l -> used.add(l.speaker())));
        DATA.boards().forEach(b -> {
            used.add(b.owner());
            b.shares().forEach(s -> used.add(String.valueOf(s.get(0))));
            b.lists().forEach(l -> l.cards().forEach(c -> {
                if (c.assignee() != null) used.add(c.assignee());
                c.comments().forEach(cm -> used.add(String.valueOf(cm.get(0))));
            }));
        });
        DATA.manualMinutes().values().forEach(mm -> mm.actions().forEach(a -> used.add(String.valueOf(a.get(1)))));
        assertThat(allowed).containsAll(used);
        // Cả 10 tài khoản mới đều xuất hiện trong dữ liệu.
        List<String> unused = new ArrayList<>(DATA.accounts().stream().map(DemoData.Account::userName).toList());
        unused.removeAll(used);
        assertThat(unused).isEmpty();
    }

    @Test
    void companyIsAbs() {
        assertThat(DATA.company()).isEqualTo("Công ty Cổ phần phần mềm ABS");
        assertThat(DATA.hostAccounts()).extracting(DemoData.HostAccount::fullName)
                .containsExactly("Ngô Viết Thuyết", "Vũ Bá Duy", "Hoàng Văn Toàn");
    }
}
