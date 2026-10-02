package vn.com.d2s.smr.repository.ad;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.d2s.smr.dto.ad.employee.EmployeeItemResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;

import java.util.List;
import java.util.Optional;

public interface AdAccountRepository extends JpaRepository<AdAccount, String> {

    Optional<AdAccount> findFirstByUserNameOrEmail(String userName, String email);

    Optional<AdAccount> findByEmail(String email);

    boolean existsByUserNameOrEmail(String userName, String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndUserNameNot(String email, String userName);

    long countByActive(boolean active);

    long countByOrgId(String orgId);

    long countByTitleCode(String titleCode);

    boolean existsByOrgId(String orgId);

    boolean existsByTitleCode(String titleCode);

    @Query("""
            select new vn.com.d2s.smr.dto.ad.employee.EmployeeItemResponse(
                a.userName, a.fullName, a.email, a.phone, a.address,
                a.orgId, o.name, a.titleCode, t.name,
                a.active, a.mustChangePassword, a.lastLoginAt, a.createDate
            )
            from AdAccount a
            left join MdOrganize o on a.orgId = o.id
            left join MdTitle t on a.titleCode = t.code
            where (:orgId is null or :orgId = '' or a.orgId = :orgId)
              and (:titleCode is null or :titleCode = '' or a.titleCode = :titleCode)
              and (:active is null or a.active = :active)
              and (:keyword is null or :keyword = '' or (
                    lower(a.userName) like lower(concat('%', :keyword, '%'))
                 or lower(a.fullName) like lower(concat('%', :keyword, '%'))
                 or lower(a.email) like lower(concat('%', :keyword, '%'))
              ))
            order by a.fullName asc
            """)
    List<EmployeeItemResponse> findEmployees(
            @Param("orgId") String orgId,
            @Param("titleCode") String titleCode,
            @Param("active") Boolean active,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select count(a.userName)
            from AdAccount a
            where (:orgId is null or :orgId = '' or a.orgId = :orgId)
              and (:titleCode is null or :titleCode = '' or a.titleCode = :titleCode)
              and (:active is null or a.active = :active)
              and (:keyword is null or :keyword = '' or (
                    lower(a.userName) like lower(concat('%', :keyword, '%'))
                 or lower(a.fullName) like lower(concat('%', :keyword, '%'))
                 or lower(a.email) like lower(concat('%', :keyword, '%'))
              ))
            """)
    long countEmployees(
            @Param("orgId") String orgId,
            @Param("titleCode") String titleCode,
            @Param("active") Boolean active,
            @Param("keyword") String keyword
    );

    @Query("""
            select account
            from AdAccount account
            where account.userName <> :currentUser
              and (
                lower(account.userName) like lower(concat('%', :query, '%'))
                or lower(account.fullName) like lower(concat('%', :query, '%'))
                or lower(account.email) like lower(concat('%', :query, '%'))
              )
            order by account.fullName
            """)
    List<AdAccount> searchUsers(
            @Param("currentUser") String currentUser,
            @Param("query") String query,
            Pageable pageable
    );
}
