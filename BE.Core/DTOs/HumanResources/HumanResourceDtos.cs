using System.ComponentModel.DataAnnotations;
using BE.Core.Authorization;

namespace BE.Core.DTOs.HumanResources
{
    public record OrganizationRequest([Required] string Name, string? ParentId, int OrderNumber, bool IsActive, string? Notes);
    public record MoveOrganizationRequest(string? ParentId, int OrderNumber);
    public record TitleRequest([Required] string Code, [Required] string Name, string? Notes, int OrderNumber, bool IsActive);
    public class EmployeeRequest
    {
        [Required, StringLength(100)] public string UserName { get; set; } = string.Empty;
        [Required, StringLength(200)] public string FullName { get; set; } = string.Empty;
        [Required, EmailAddress] public string Email { get; set; } = string.Empty;
        public string Phone { get; set; } = string.Empty; public string Address { get; set; } = string.Empty;
        [Required] public string OrganizationId { get; set; } = string.Empty; [Required] public string TitleCode { get; set; } = string.Empty;
    }
    public record UpdateEmployeeRequest(string FullName, string Email, string Phone, string Address, string OrganizationId, string TitleCode);
    public class CreateDirectoryEmployeeRequest
    {
        [Required, StringLength(200)] public string FullName { get; set; } = string.Empty;
        [Required, EmailAddress] public string Email { get; set; } = string.Empty;
        public string Phone { get; set; } = string.Empty; public string Address { get; set; } = string.Empty;
        [Required] public string OrganizationId { get; set; } = string.Empty; [Required] public string TitleCode { get; set; } = string.Empty;
    }
    public record TransferEmployeeRequest(string OrganizationId, string? Reason, DateTime? EffectiveDate);
    public record ChangeTitleRequest(string TitleCode);
    public record ChangeStatusRequest(bool IsActive, string? Reason);
    public record PermissionUpdateRequest(int Version, Dictionary<string, PermissionEffect> Permissions);
}
