package vn.com.d2s.smr.controller.md;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
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
import vn.com.d2s.smr.dto.md.hr.HumanResourceSummaryResponse;
import vn.com.d2s.smr.dto.md.hr.MoveOrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrganizationNodeResponse;
import vn.com.d2s.smr.dto.md.hr.OrganizationRequest;
import vn.com.d2s.smr.dto.md.hr.OrganizationResponse;
import vn.com.d2s.smr.dto.md.hr.TitleDetailResponse;
import vn.com.d2s.smr.dto.md.hr.TitleRequest;
import vn.com.d2s.smr.dto.md.hr.TitleResponse;
import vn.com.d2s.smr.service.ad.EmployeeService;
import vn.com.d2s.smr.service.md.HumanResourceMasterDataService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/human-resources")
public class HumanResourcesController {

    private final HumanResourceMasterDataService hrMasterDataService;
    private final EmployeeService employeeService;

    public HumanResourcesController(
            HumanResourceMasterDataService hrMasterDataService,
            EmployeeService employeeService
    ) {
        this.hrMasterDataService = hrMasterDataService;
        this.employeeService = employeeService;
    }

    @GetMapping("/summary")
    public HumanResourceSummaryResponse summary() {
        return hrMasterDataService.summary();
    }

    @GetMapping("/organization-tree")
    public List<OrganizationNodeResponse> organizations() {
        return hrMasterDataService.getOrganizationTree();
    }

    @PostMapping("/organizations")
    public OrganizationResponse createOrg(
            Principal principal,
            @Valid @RequestBody OrganizationRequest request
    ) {
        return hrMasterDataService.createOrganization(request, principal.getName());
    }

    @PutMapping("/organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateOrg(
            Principal principal,
            @PathVariable String id,
            @Valid @RequestBody OrganizationRequest request
    ) {
        hrMasterDataService.updateOrganization(id, request, principal.getName());
    }

    @PutMapping("/organizations/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveOrg(
            Principal principal,
            @PathVariable String id,
            @RequestBody MoveOrganizationRequest request
    ) {
        hrMasterDataService.moveOrganization(id, request, principal.getName());
    }

    @DeleteMapping("/organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrg(@PathVariable String id) {
        hrMasterDataService.deleteOrganization(id);
    }

    @GetMapping("/titles")
    public List<TitleResponse> titles() {
        return hrMasterDataService.getTitles();
    }

    @PostMapping("/titles")
    public TitleDetailResponse createTitle(
            Principal principal,
            @Valid @RequestBody TitleRequest request
    ) {
        return hrMasterDataService.createTitle(request, principal.getName());
    }

    @PutMapping("/titles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTitle(
            Principal principal,
            @PathVariable String code,
            @Valid @RequestBody TitleRequest request
    ) {
        hrMasterDataService.updateTitle(code, request, principal.getName());
    }

    @DeleteMapping("/titles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTitle(@PathVariable String code) {
        hrMasterDataService.deleteTitle(code);
    }

    @GetMapping("/employees")
    public PagedResultResponse<EmployeeItemResponse> searchEmployees(
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String titleCode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return employeeService.searchEmployees(organizationId, titleCode, active, keyword, page, pageSize);
    }

    @GetMapping("/employees/{userName}")
    public EmployeeDetailResponse getEmployee(@PathVariable String userName) {
        return employeeService.getEmployee(userName);
    }

    @PostMapping("/employees")
    public CreatedEmployeeResponse createEmployee(
            Principal principal,
            @Valid @RequestBody EmployeeRequest request
    ) {
        return employeeService.createEmployee(request, principal.getName());
    }

    @PostMapping("/directory-employees")
    public CreatedDirectoryEmployeeResponse createDirectoryEmployee(
            Principal principal,
            @Valid @RequestBody CreateDirectoryEmployeeRequest request
    ) {
        return employeeService.createDirectoryEmployee(request, principal.getName());
    }

    @PutMapping("/employees/{userName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateEmployee(
            Principal principal,
            @PathVariable String userName,
            @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        employeeService.updateEmployee(userName, request, principal.getName());
    }

    @PutMapping("/employees/{userName}/organization")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferEmployee(
            Principal principal,
            @PathVariable String userName,
            @Valid @RequestBody TransferEmployeeRequest request
    ) {
        employeeService.transferEmployee(userName, request, principal.getName());
    }

    @PutMapping("/employees/{userName}/title")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeTitle(
            Principal principal,
            @PathVariable String userName,
            @Valid @RequestBody ChangeTitleRequest request
    ) {
        employeeService.changeEmployeeTitle(userName, request, principal.getName());
    }

    @PutMapping("/employees/{userName}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(
            Principal principal,
            @PathVariable String userName,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        employeeService.changeEmployeeStatus(userName, request, principal.getName());
    }

    @PostMapping("/employees/{userName}/reset-password")
    public ResetEmployeePasswordResponse resetPassword(
            Principal principal,
            @PathVariable String userName
    ) {
        return employeeService.resetEmployeePassword(userName, principal.getName());
    }

    @GetMapping("/{type}/{id}/permissions")
    public PermissionDocument getPermission(
            @PathVariable String type,
            @PathVariable String id
    ) {
        return employeeService.getPermission(type, id);
    }

    @PutMapping("/{type}/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void savePermission(
            Principal principal,
            @PathVariable String type,
            @PathVariable String id,
            @Valid @RequestBody PermissionUpdateRequest request
    ) {
        employeeService.savePermission(type, id, request, principal.getName());
    }

    @GetMapping("/employees/{userName}/effective-permissions")
    public List<EffectivePermissionResponse> getEffectivePermissions(@PathVariable String userName) {
        return employeeService.getEffectivePermissions(userName);
    }
}
