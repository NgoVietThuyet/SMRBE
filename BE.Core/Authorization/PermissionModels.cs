using System.Text.Json.Serialization;

namespace BE.Core.Authorization
{
    [JsonConverter(typeof(JsonStringEnumConverter))]
    public enum PermissionEffect { Inherit = 0, Allow = 1, Deny = 2 }

    public sealed class PermissionDocument
    {
        public int Version { get; set; } = 1;
        public Dictionary<string, PermissionEffect> Permissions { get; set; } = new(StringComparer.OrdinalIgnoreCase);
    }

    public record PermissionDefinition(string Code, string Name, string Group);
    public record EffectivePermissionDto(string Code, string Name, bool Allowed, string Effect, string SourceType, string? SourceId, string? SourceName);
}
