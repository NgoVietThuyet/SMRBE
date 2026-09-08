using BE.Core.Data;
using BE.Core.Entities.CF;
using BE.Infrastructure;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BE.API.Controllers
{
    [ApiController, Route("api/[controller]")]
    public class FileController : ControllerBase
    {
        private static readonly HashSet<string> RecordingExtensions = new(StringComparer.OrdinalIgnoreCase) { ".mp4", ".webm", ".mkv", ".mp3", ".wav", ".ogg" };
        private const long MaxSize = 2L * 1024 * 1024 * 1024;
        private readonly AppDbContext _db; private readonly IObjectStorageService _storage;
        public FileController(AppDbContext db, IObjectStorageService storage) { _db = db; _storage = storage; }

        [AllowAnonymous, RequestSizeLimit(MaxSize), HttpPost("UploadFilesRecord")]
        public async Task<IActionResult> UploadFilesRecord([FromForm] string meetingId, [FromForm] List<IFormFile> files, CancellationToken ct)
        {
            var meeting = await _db.MeetingInfos.AsNoTracking().FirstOrDefaultAsync(m => m.Id == meetingId, ct);
            if (meeting == null) return NotFound(new { message = "Không tìm thấy cuộc họp." });
            if (files.Count == 0) return BadRequest(new { message = "Không có file recording." });
            var result = new List<object>();
            foreach (var file in files)
            {
                var ext = Path.GetExtension(file.FileName);
                if (file.Length <= 0 || file.Length > MaxSize || !RecordingExtensions.Contains(ext)) return BadRequest(new { message = $"File {file.FileName} không hợp lệ." });
                var id = Guid.NewGuid().ToString("N"); var objectName = $"meetings/{meetingId}/recordings/{id}{ext.ToLowerInvariant()}";
                await using var stream = file.OpenReadStream();
                await _storage.UploadAsync(objectName, stream, file.Length, file.ContentType ?? "application/octet-stream", ct);
                var entity = new CmFile
                {
                    Id = id,
                    FileName = Path.GetFileName(file.FileName),
                    FileSize = file.Length,
                    MimeType = file.ContentType ?? "application/octet-stream",
                    Extention = ext,
                    Type = 2,
                    Icon = "recording",
                    RefrenceFileId = meeting.RefrenceFileId,
                    IsBienBan = false,
                    OrderNumber = 0,
                    VoiceToText = string.Empty,
                    BucketName = _storage.BucketName,
                    ObjectName = objectName,
                    CreateBy = "jibri",
                    CreateDate = DateTime.UtcNow,
                    UpdateBy = "jibri",
                    UpdateDate = DateTime.UtcNow
                };
                _db.CmFiles.Add(entity);
                try { await _db.SaveChangesAsync(ct); } catch { await _storage.DeleteAsync(objectName, ct); throw; }
                result.Add(new { entity.Id, entity.FileName, entity.FileSize, entity.MimeType });
            }
            return Ok(result);
        }

        [Authorize, HttpGet("GetMeetingFiles/{meetingId}")]
        public async Task<IActionResult> GetMeetingFiles(string meetingId)
        {
            var user = User.Identity?.Name;
            if (!await _db.MeetingPersonals.AnyAsync(p => p.MeetingId == meetingId && p.UserName == user)) return Forbid();
            var reference = await _db.MeetingInfos.Where(m => m.Id == meetingId).Select(m => m.RefrenceFileId).FirstOrDefaultAsync();
            return Ok(await _db.CmFiles.AsNoTracking().Where(f => f.RefrenceFileId == reference).OrderByDescending(f => f.CreateDate)
                .Select(f => new { f.Id, f.FileName, f.FileSize, f.MimeType, f.Type, f.CreateDate }).ToListAsync());
        }

        [Authorize, HttpGet("Download/{fileId}")]
        public async Task<IActionResult> Download(string fileId)
        {
            var file = await _db.CmFiles.AsNoTracking().FirstOrDefaultAsync(f => f.Id == fileId); if (file == null) return NotFound();
            var meetingId = await _db.MeetingInfos.Where(m => m.RefrenceFileId == file.RefrenceFileId).Select(m => m.Id).FirstOrDefaultAsync();
            if (!await _db.MeetingPersonals.AnyAsync(p => p.MeetingId == meetingId && p.UserName == User.Identity!.Name)) return Forbid();
            return Ok(new { url = await _storage.GetDownloadUrlAsync(file.ObjectName), expiresIn = 900 });
        }
    }
}
