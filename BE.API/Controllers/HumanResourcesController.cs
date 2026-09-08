using BE.API.Authorization;
using BE.Core.Authorization;
using BE.Core.DTOs.HumanResources;
using BE.Service.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BE.API.Controllers
{
    [Authorize, ApiController, Route("api/human-resources")]
    public class HumanResourcesController : ControllerBase
    {
        private readonly HumanResourceService _service; private string UserName => User.Identity!.Name!;
        public HumanResourcesController(HumanResourceService service) => _service = service;
        [RequirePermission(PermissionCodes.HrView), HttpGet("summary")] public Task<IActionResult> Summary() => Run(async () => Ok(await _service.Summary()));
        [RequirePermission(PermissionCodes.HrView), HttpGet("organization-tree")] public Task<IActionResult> Organizations() => Run(async () => Ok(await _service.Organizations()));
        [RequirePermission(PermissionCodes.HrOrgCreate), HttpPost("organizations")] public Task<IActionResult> CreateOrg(OrganizationRequest r) => Run(async () => Ok(await _service.CreateOrganization(r, UserName)));
        [RequirePermission(PermissionCodes.HrOrgUpdate), HttpPut("organizations/{id}")] public Task<IActionResult> UpdateOrg(string id, OrganizationRequest r) => Run(async () => { await _service.UpdateOrganization(id, r, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrOrgMove), HttpPut("organizations/{id}/move")] public Task<IActionResult> MoveOrg(string id, MoveOrganizationRequest r) => Run(async () => { await _service.MoveOrganization(id, r, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrOrgDelete), HttpDelete("organizations/{id}")] public Task<IActionResult> DeleteOrg(string id) => Run(async () => { await _service.DeleteOrganization(id); return NoContent(); });
        [RequirePermission(PermissionCodes.HrTitleView), HttpGet("titles")] public Task<IActionResult> Titles() => Run(async () => Ok(await _service.Titles()));
        [RequirePermission(PermissionCodes.HrTitleCreate), HttpPost("titles")] public Task<IActionResult> CreateTitle(TitleRequest r) => Run(async () => Ok(await _service.CreateTitle(r, UserName)));
        [RequirePermission(PermissionCodes.HrTitleUpdate), HttpPut("titles/{code}")] public Task<IActionResult> UpdateTitle(string code, TitleRequest r) => Run(async () => { await _service.UpdateTitle(code, r, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrTitleDelete), HttpDelete("titles/{code}")] public Task<IActionResult> DeleteTitle(string code) => Run(async () => { await _service.DeleteTitle(code); return NoContent(); });
        [RequirePermission(PermissionCodes.HrAccountView), HttpGet("employees")] public Task<IActionResult> Employees([FromQuery] string? organizationId, [FromQuery] string? titleCode, [FromQuery] bool? active, [FromQuery] string? keyword, [FromQuery] int page = 1, [FromQuery] int pageSize = 20) => Run(async () => Ok(await _service.Employees(organizationId, titleCode, active, keyword, page, pageSize)));
        [RequirePermission(PermissionCodes.HrAccountView), HttpGet("employees/{userName}")] public Task<IActionResult> Employee(string userName) => Run(async () => Ok(await _service.Employee(userName)));
        [RequirePermission(PermissionCodes.HrAccountCreate), HttpPost("employees")] public Task<IActionResult> CreateEmployee(EmployeeRequest r) => Run(async () => Ok(await _service.CreateEmployee(r, UserName)));
        [RequirePermission(PermissionCodes.HrAccountUpdate), HttpPut("employees/{userName}")] public Task<IActionResult> UpdateEmployee(string userName, UpdateEmployeeRequest r) => Run(async () => { await _service.UpdateEmployee(userName, r, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrAccountTransfer), HttpPut("employees/{userName}/organization")] public Task<IActionResult> Transfer(string userName, TransferEmployeeRequest r) => Run(async () => { await _service.Transfer(userName, r, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrAccountChangeTitle), HttpPut("employees/{userName}/title")] public Task<IActionResult> ChangeTitle(string userName, ChangeTitleRequest r) => Run(async () => { await _service.ChangeTitle(userName, r, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrAccountLock), HttpPut("employees/{userName}/status")] public Task<IActionResult> Status(string userName, ChangeStatusRequest r) => Run(async () => { await _service.ChangeStatus(userName, r, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrAccountResetPassword), HttpPost("employees/{userName}/reset-password")] public Task<IActionResult> Reset(string userName) => Run(async () => Ok(await _service.ResetPassword(userName, UserName)));
        [RequirePermission(PermissionCodes.HrView), HttpGet("{type}/{id}/permissions")] public Task<IActionResult> GetPermission(string type, string id) => Run(async () => Ok(await _service.GetPermission(type, id)));
        [RequirePermission(PermissionCodes.HrAccountPermission), HttpPut("{type}/{id}/permissions")] public Task<IActionResult> SavePermission(string type, string id, PermissionUpdateRequest r) => Run(async () => { await _service.SavePermission(type, id, new PermissionDocument { Version = r.Version, Permissions = r.Permissions }, UserName); return NoContent(); });
        [RequirePermission(PermissionCodes.HrAccountView), HttpGet("employees/{userName}/effective-permissions")] public async Task<IActionResult> Effective(string userName, [FromServices] BE.Core.Data.IPermissionService permissions) => Ok(await permissions.GetEffectivePermissionsAsync(userName));
        private static async Task<IActionResult> Run(Func<Task<IActionResult>> f) { try { return await f(); } catch (ArgumentException e) { return new BadRequestObjectResult(new { message = e.Message }); } catch (InvalidOperationException e) { return new ConflictObjectResult(new { message = e.Message }); } catch (KeyNotFoundException e) { return new NotFoundObjectResult(new { message = e.Message }); } }
    }
}
