package vn.com.d2s.smr.repository.md;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.com.d2s.smr.dto.md.hr.OrgEmployeeCountResponse;
import vn.com.d2s.smr.entity.md.MdOrganize;

import java.util.List;

public interface MdOrganizeRepository extends JpaRepository<MdOrganize, String> {

    List<MdOrganize> findAllByOrderByOrderNumberAscNameAsc();

    boolean existsByParentId(String parentId);

    boolean existsByIdAndActiveTrue(String id);

    @Query("""
            select new vn.com.d2s.smr.dto.md.hr.OrgEmployeeCountResponse(
                o.id, o.name, count(a.userName)
            )
            from MdOrganize o
            left join AdAccount a on o.id = a.orgId
            group by o.id, o.name
            order by count(a.userName) desc
            """)
    List<OrgEmployeeCountResponse> getOrgEmployeeCounts();
}
