using BE.Core.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.Options;

namespace BE.API.Authorization
{
    public class PermissionRequirement(string code) : IAuthorizationRequirement { public string Code { get; } = code; }
    public class PermissionHandler(IPermissionService service) : AuthorizationHandler<PermissionRequirement>
    { protected override async Task HandleRequirementAsync(AuthorizationHandlerContext context, PermissionRequirement requirement) { var user = context.User.Identity?.Name; if (user != null && await service.HasPermissionAsync(user, requirement.Code)) context.Succeed(requirement); } }
    public class PermissionPolicyProvider(IOptions<AuthorizationOptions> options) : DefaultAuthorizationPolicyProvider(options)
    { public const string Prefix = "Permission:"; public override async Task<AuthorizationPolicy?> GetPolicyAsync(string name) { if (name.StartsWith(Prefix, StringComparison.OrdinalIgnoreCase)) { var policy = new AuthorizationPolicyBuilder().RequireAuthenticatedUser().AddRequirements(new PermissionRequirement(name[Prefix.Length..])).Build(); return policy; } return await base.GetPolicyAsync(name); } }
    public sealed class RequirePermissionAttribute : AuthorizeAttribute { public RequirePermissionAttribute(string code) => Policy = PermissionPolicyProvider.Prefix + code; }
}
