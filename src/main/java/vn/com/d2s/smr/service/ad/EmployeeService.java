package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.dto.ad.employee.ChangeStatusRequest;
import vn.com.d2s.smr.dto.ad.employee.ChangeTitleRequest;
import vn.com.d2s.smr.dto.ad.employee.CreateDirectoryEmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.CreatedDirectoryEmployeeResponse;
import vn.com.d2s.smr.dto.ad.employee.CreatedEmployeeResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeDetailResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeItemResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.ResetEmployeePasswordResponse;
import vn.com.d2s.smr.dto.ad.employee.TransferEmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.UpdateEmployeeRequest;
import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionDocument;
import vn.com.d2s.smr.dto.ad.permission.PermissionUpdateRequest;
import vn.com.d2s.smr.dto.common.PagedResultResponse;

import java.util.List;

public interface EmployeeService {

    PagedResultResponse<EmployeeItemResponse> searchEmployees(
            String organizationId,
            String titleCode,
            Boolean active,
            String keyword,
            int page,
            int pageSize
    );

    EmployeeDetailResponse getEmployee(String userName);

    CreatedEmployeeResponse createEmployee(EmployeeRequest request, String actor);

    CreatedDirectoryEmployeeResponse createDirectoryEmployee(CreateDirectoryEmployeeRequest request, String actor);

    void updateEmployee(String userName, UpdateEmployeeRequest request, String actor);

    void transferEmployee(String userName, TransferEmployeeRequest request, String actor);

    void changeEmployeeTitle(String userName, ChangeTitleRequest request, String actor);

    void changeEmployeeStatus(String userName, ChangeStatusRequest request, String actor);

    ResetEmployeePasswordResponse resetEmployeePassword(String userName, String actor);

    PermissionDocument getPermission(String type, String id);

    void savePermission(String type, String id, PermissionUpdateRequest request, String actor);

    List<EffectivePermissionResponse> getEffectivePermissions(String userName);
}
