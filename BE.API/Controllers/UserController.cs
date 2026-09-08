using BE.Infrastructure;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BE.API.Controllers
{
    [Authorize]
    [ApiController]
    [Route("api/[controller]")]
    public class UserController : ControllerBase
    {
        private readonly AppDbContext _db;
        public UserController(AppDbContext db) => _db = db;

        [HttpGet("Search")]
        public async Task<IActionResult> Search([FromQuery] string? q, [FromQuery] int take = 10)
        {
            var query = (q ?? string.Empty).Trim();
            if (query.Length < 1) return Ok(Array.Empty<object>());
            take = Math.Clamp(take, 1, 20);
            var currentUser = User.Identity?.Name;
            var users = await _db.AdAccounts.AsNoTracking()
                .Where(a => a.UserName != currentUser &&
                    (a.UserName.Contains(query) || a.FullName.Contains(query) || a.Email.Contains(query)))
                .OrderBy(a => a.FullName).Take(take)
                .Select(a => new { a.UserName, a.FullName, a.Email }).ToListAsync();
            return Ok(users);
        }
    }
}
