using System.Text.Json;
using BE.Core.Authorization;
using BE.Core.Data;
using BE.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace BE.Service.Services
{
    public class PermissionService : IPermissionService
    {
        private readonly AppDbContext _db; private readonly ILogger<PermissionService> _logger;
        private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true, WriteIndented = true, Converters = { new System.Text.Json.Serialization.JsonStringEnumConverter() } };
        public PermissionService(AppDbContext db, ILogger<PermissionService> logger) { _db = db; _logger = logger; }

        public PermissionDocument ParseAndValidate(string? json)
        {
            if (string.IsNullOrWhiteSpace(json)) return new PermissionDocument();
            try { var doc = JsonSerializer.Deserialize<PermissionDocument>(json, JsonOptions) ?? new(); Validate(doc); return doc; }
            catch (JsonException ex) { _logger.LogError(ex, "Invalid permission JSON"); throw new ArgumentException("JSON quyền không hợp lệ."); }
        }
        public string SerializeAndValidate(PermissionDocument document) { Validate(document); document.Permissions = document.Permissions.ToDictionary(x => x.Key.ToUpperInvariant(), x => x.Value, StringComparer.OrdinalIgnoreCase); return JsonSerializer.Serialize(document, JsonOptions); }
        private static void Validate(PermissionDocument doc) { if (doc.Version != 1) throw new ArgumentException("Phiên bản permission JSON không được hỗ trợ."); var invalid = doc.Permissions.Keys.Where(x => !PermissionCodes.All.Contains(x)).ToList(); if (invalid.Count > 0) throw new ArgumentException($"Mã quyền không hợp lệ: {string.Join(", ", invalid)}"); }

        public async Task<bool> HasPermissionAsync(string userName, string code, CancellationToken ct = default)
        {
            var account = await _db.AdAccounts.AsNoTracking().FirstOrDefaultAsync(x => x.UserName == userName, ct); if (account == null || !account.IsActive) return false;
            if (IsSuperAdmin(account.RoleCodes)) return true;
            var title = await _db.MdTitles.AsNoTracking().FirstOrDefaultAsync(x => x.Code == account.TitleCode, ct);
            var org = await _db.MdOrganizes.AsNoTracking().FirstOrDefaultAsync(x => x.Id == account.OrgId, ct);
            return Resolve(code, account.PermissionJson, title?.PermissionJson, org?.PermissionJson).allowed;
        }
        public async Task<IReadOnlyCollection<EffectivePermissionDto>> GetEffectivePermissionsAsync(string userName, CancellationToken ct = default)
        {
            var account = await _db.AdAccounts.AsNoTracking().FirstOrDefaultAsync(x => x.UserName == userName, ct) ?? throw new KeyNotFoundException("Không tìm thấy tài khoản.");
            var title = await _db.MdTitles.AsNoTracking().FirstOrDefaultAsync(x => x.Code == account.TitleCode, ct); var org = await _db.MdOrganizes.AsNoTracking().FirstOrDefaultAsync(x => x.Id == account.OrgId, ct);
            return PermissionCodes.Catalog.Select(p => { if (IsSuperAdmin(account.RoleCodes)) return new EffectivePermissionDto(p.Code, p.Name, true, "Allow", "ROLE", "SUPER_ADMIN", "Quản trị hệ thống"); var r = Resolve(p.Code, account.PermissionJson, title?.PermissionJson, org?.PermissionJson); return new EffectivePermissionDto(p.Code, p.Name, r.allowed, r.effect.ToString(), r.source, r.source == "USER" ? account.UserName : r.source == "TITLE" ? title?.Code : org?.Id, r.source == "USER" ? account.FullName : r.source == "TITLE" ? title?.Name : org?.Name); }).ToList();
        }
        private (bool allowed, PermissionEffect effect, string source) Resolve(string code, string? user, string? title, string? org)
        { foreach (var x in new[] { (user, "USER"), (title, "TITLE"), (org, "ORGANIZATION") }) { PermissionDocument d; try { d = ParseAndValidate(x.Item1); } catch { return (false, PermissionEffect.Deny, "INVALID_JSON"); } if (d.Permissions.TryGetValue(code, out var e) && e != PermissionEffect.Inherit) return (e == PermissionEffect.Allow, e, x.Item2); } return (false, PermissionEffect.Deny, "DEFAULT"); }
        private static bool IsSuperAdmin(string? roles) => !string.IsNullOrWhiteSpace(roles) && roles.Contains("SUPER_ADMIN", StringComparison.OrdinalIgnoreCase);
    }
}
