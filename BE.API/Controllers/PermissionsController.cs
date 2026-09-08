using BE.Core.Authorization;
using BE.Core.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BE.API.Controllers
{
    [Authorize, ApiController, Route("api/permissions")]
    public class PermissionsController(IPermissionService service) : ControllerBase
    {
        [HttpGet("catalog")] public IActionResult Catalog() => Ok(PermissionCodes.Catalog.GroupBy(x => x.Group).Select(g => new { groupName = g.Key, permissions = g }));
        [HttpGet("me")] public async Task<IActionResult> Me() => Ok(await service.GetEffectivePermissionsAsync(User.Identity!.Name!));
    }
}
