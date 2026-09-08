using BE.Core.Authorization;

namespace BE.Core.Data
{
    public interface IPermissionService
    {
        Task<bool> HasPermissionAsync(string userName, string permissionCode, CancellationToken ct = default);
        Task<IReadOnlyCollection<EffectivePermissionDto>> GetEffectivePermissionsAsync(string userName, CancellationToken ct = default);
        PermissionDocument ParseAndValidate(string? json);
        string SerializeAndValidate(PermissionDocument document);
    }
}
