using System;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using System.Threading.Tasks;
using BE.Core.Data;
using BE.Core.DTOs.Auth;
using BE.Core.Entities.AD;
using BE.Core.Entities.MT;
using BE.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.Extensions.Configuration;

namespace BE.Service.Services
{
    /// <summary>
    /// Lớp hiện thực hóa dịch vụ Xác thực và Quản lý phiên (AUTH).
    /// </summary>
    public class AuthService : IAuthService
    {
        private readonly AppDbContext _db;
        private readonly IConfiguration _config;

        /// <summary>
        /// Khởi tạo AuthService với DbContext và Configuration.
        /// </summary>
        public AuthService(AppDbContext db, IConfiguration config)
        {
            _db = db;
            _config = config;
        }

        /// <summary>
        /// Đăng ký tài khoản người dùng nội bộ mới.
        /// </summary>
        public async Task<AuthResultDto> RegisterAsync(RegisterDto dto)
        {
            var userName = dto.UserName.Trim();
            var email = dto.Email.Trim();
            if (await _db.AdAccounts.AnyAsync(a => a.UserName == userName || a.Email == email))
                return Fail("Tên đăng nhập hoặc email đã tồn tại.");

            var now = DateTime.UtcNow;
            var account = new AdAccount
            {
                UserName = userName,
                Password = BCrypt.Net.BCrypt.HashPassword(dto.Password),
                FullName = dto.FullName.Trim(),
                Email = email,
                Phone = dto.Phone ?? string.Empty,
                Address = string.Empty,
                OrgId = "ROOT",
                TitleCode = "ADMIN",
                IsActive = true,
                MustChangePassword = false,
                TokenVersion = 1,
                CreateBy = userName,
                CreateDate = now,
                UpdateBy = userName,
                UpdateDate = now
            };
            _db.Add(account);
            await _db.SaveChangesAsync();
            return Success(account, "Đăng ký thành công.");
        }

        /// <summary>
        /// Đăng nhập tài khoản bằng Username/Email và Password.
        /// </summary>
        public async Task<AuthResultDto> LoginAsync(LoginDto dto)
        {
            var login = dto.UserName.Trim();
            var account = await _db.AdAccounts.FirstOrDefaultAsync(a => a.UserName == login || a.Email == login);
            if (account == null) return Fail("Tài khoản không tồn tại.");
            if (!account.IsActive) return Fail("Tài khoản đã bị khóa.");
            if (!BCrypt.Net.BCrypt.Verify(dto.Password, account.Password)) return Fail("Mật khẩu không chính xác.");

            account.LastLoginAt = DateTime.UtcNow;
            account.UpdateDate = DateTime.UtcNow;
            await _db.SaveChangesAsync();
            return Success(account, "Đăng nhập thành công.");
        }

        /// <summary>
        /// Đổi mật khẩu cá nhân cho người dùng đang đăng nhập.
        /// </summary>
        public async Task<AuthResultDto> ChangePasswordAsync(string userName, ChangePasswordDto dto)
        {
            var account = await _db.AdAccounts.FirstOrDefaultAsync(a => a.UserName == userName);
            if (account == null || !account.IsActive) return Fail("Tài khoản không tồn tại hoặc đã bị khóa.");
            if (!BCrypt.Net.BCrypt.Verify(dto.CurrentPassword, account.Password)) return Fail("Mật khẩu hiện tại không chính xác.");
            if (dto.NewPassword.Length < 8) return Fail("Mật khẩu mới phải có ít nhất 8 ký tự.");

            account.Password = BCrypt.Net.BCrypt.HashPassword(dto.NewPassword);
            account.MustChangePassword = false;
            account.TokenVersion++;
            account.UpdateBy = userName;
            account.UpdateDate = DateTime.UtcNow;
            await _db.SaveChangesAsync();
            return Success(account, "Đổi mật khẩu thành công. Vui lòng đăng nhập lại.");
        }

        /// <summary>
        /// Làm mới Access Token thông qua xoay tua Refresh Token.
        /// </summary>
        public async Task<AuthResultDto> RefreshAsync(RefreshDto dto)
        {
            var principal = ValidateToken(dto.RefreshToken, "refresh");
            if (principal == null) return Fail("Refresh token không hợp lệ hoặc đã hết hạn.");

            var userName = principal.Identity?.Name;
            if (string.IsNullOrEmpty(userName)) return Fail("Refresh token không hợp lệ.");

            var account = await _db.AdAccounts.FirstOrDefaultAsync(a => a.UserName == userName);
            if (account == null || !account.IsActive) return Fail("Tài khoản không tồn tại hoặc đã bị khóa.");

            account.UpdateDate = DateTime.UtcNow;
            await _db.SaveChangesAsync();

            return new AuthResultDto
            {
                Success = true,
                Message = "Làm mới token thành công.",
                Token = CreateToken(account),
                RefreshToken = CreateRefreshToken(account.UserName),
                UserName = account.UserName,
                FullName = account.FullName,
                Email = account.Email,
                MustChangePassword = account.MustChangePassword
            };
        }

        /// <summary>
        /// Đăng xuất tài khoản khỏi phiên hiện tại hoặc toàn bộ thiết bị.
        /// </summary>
        public async Task<AuthResultDto> LogoutAsync(string userName, LogoutDto dto)
        {
            var account = await _db.AdAccounts.FirstOrDefaultAsync(a => a.UserName == userName);
            if (account == null) return Fail("Tài khoản không tồn tại.");

            if (dto.LogoutAllDevices)
            {
                account.TokenVersion++;
                account.UpdateBy = userName;
                account.UpdateDate = DateTime.UtcNow;
                await _db.SaveChangesAsync();
                return new AuthResultDto { Success = true, Message = "Đăng xuất khỏi tất cả các thiết bị thành công." };
            }

            return new AuthResultDto { Success = true, Message = "Đăng xuất thành công." };
        }

        /// <summary>
        /// Khởi tạo yêu cầu quên mật khẩu, sinh sinh token khôi phục.
        /// </summary>
        public async Task<AuthResultDto> ForgotPasswordAsync(ForgotPasswordDto dto)
        {
            var email = (dto.Email ?? string.Empty).Trim();
            var account = await _db.AdAccounts.FirstOrDefaultAsync(a => a.Email == email);

            // Luôn trả về thành công để tránh brute force hoặc dò tìm email tồn tại
            if (account == null || !account.IsActive)
            {
                return new AuthResultDto
                {
                    Success = true,
                    Message = "Yêu cầu khôi phục mật khẩu đã được tiếp nhận. Vui lòng kiểm tra hộp thư email (nếu email tồn tại trong hệ thống)."
                };
            }

            var resetToken = CreateResetToken(account.UserName);

            return new AuthResultDto
            {
                Success = true,
                Message = "Yêu cầu khôi phục mật khẩu đã được tiếp nhận. Vui lòng sử dụng token được cung cấp để đặt lại mật khẩu.",
                Token = resetToken
            };
        }

        /// <summary>
        /// Đặt lại mật khẩu mới thông qua Token bảo mật.
        /// </summary>
        public async Task<AuthResultDto> ResetPasswordAsync(ResetPasswordDto dto)
        {
            var principal = ValidateToken(dto.ResetToken, "reset");
            if (principal == null) return Fail("Token khôi phục không hợp lệ hoặc đã hết hạn.");

            var userName = principal.Identity?.Name;
            if (string.IsNullOrEmpty(userName)) return Fail("Token khôi phục không hợp lệ.");

            var account = await _db.AdAccounts.FirstOrDefaultAsync(a => a.UserName == userName);
            if (account == null || !account.IsActive) return Fail("Tài khoản không tồn tại hoặc đã bị khóa.");

            if (dto.NewPassword.Length < 8) return Fail("Mật khẩu mới phải có ít nhất 8 ký tự.");

            account.Password = BCrypt.Net.BCrypt.HashPassword(dto.NewPassword);
            account.TokenVersion++; // Khi đổi mật khẩu thành công, thu hồi tất cả token cũ
            account.UpdateBy = userName;
            account.UpdateDate = DateTime.UtcNow;
            await _db.SaveChangesAsync();

            return new AuthResultDto
            {
                Success = true,
                Message = "Đặt lại mật khẩu thành công. Các phiên truy cập cũ đã được thu hồi."
            };
        }

        /// <summary>
        /// Cấp phiên truy cập và token tham gia cuộc họp cho khách vãng lai.
        /// </summary>
        public async Task<AuthResultDto> GuestSessionAsync(GuestSessionDto dto)
        {
            var meetingId = (dto.MeetingId ?? string.Empty).Trim();
            var displayName = (dto.DisplayName ?? string.Empty).Trim();

            if (string.IsNullOrEmpty(meetingId) || string.IsNullOrEmpty(displayName))
                return Fail("Mã cuộc họp và tên hiển thị không được để trống.");

            var meeting = await _db.MeetingInfos.FirstOrDefaultAsync(m => m.Id == meetingId);
            if (meeting == null) return Fail("Cuộc họp không tồn tại.");
            if (meeting.Status == 3) return Fail("Cuộc họp đã kết thúc. Khách không thể tham gia.");

            // Thêm khách vào danh sách thành viên họp tạm thời để liên kết chat và realtime
            var guestUserName = "guest_" + Guid.NewGuid().ToString("N").Substring(0, 8);
            var now = DateTime.UtcNow;
            var guestPersonal = new MeetingPersonal
            {
                Id = Guid.NewGuid().ToString("N"),
                MeetingId = meeting.Id,
                UserName = guestUserName,
                FullName = displayName,
                Phone = string.Empty,
                Email = string.Empty,
                Address = string.Empty,
                OrgId = string.Empty,
                TitleCode = string.Empty,
                RefrenceFileId = meeting.RefrenceFileId,
                Type = 3, // Loại 3: Guest
                IsChuTri = false,
                IsJoined = true,
                JoinTime = now,
                CreateBy = guestUserName,
                CreateDate = now,
                UpdateBy = guestUserName,
                UpdateDate = now
            };

            _db.MeetingPersonals.Add(guestPersonal);
            await _db.SaveChangesAsync();

            var token = CreateGuestToken(guestUserName, displayName, meeting.Id);

            return new AuthResultDto
            {
                Success = true,
                Message = "Tham gia cuộc họp với vai trò khách thành công.",
                Token = token,
                UserName = guestUserName,
                FullName = displayName,
                MustChangePassword = false
            };
        }

        private AuthResultDto Success(AdAccount account, string message) => new()
        {
            Success = true,
            Message = message,
            Token = CreateToken(account),
            RefreshToken = CreateRefreshToken(account.UserName),
            UserName = account.UserName,
            FullName = account.FullName,
            Email = account.Email,
            MustChangePassword = account.MustChangePassword
        };

        private static AuthResultDto Fail(string message) => new() { Success = false, Message = message };

        private string CreateToken(AdAccount account)
        {
            var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(
                _config["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured.")));
            var claims = new[]
            {
                new Claim(ClaimTypes.Name, account.UserName),
                new Claim(ClaimTypes.Email, account.Email ?? string.Empty),
                new Claim("FullName", account.FullName ?? string.Empty),
                new Claim("TokenVersion", account.TokenVersion.ToString()),
                new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
            };
            var token = new JwtSecurityToken(_config["Jwt:Issuer"], _config["Jwt:Audience"], claims,
                expires: DateTime.UtcNow.AddMinutes(int.Parse(_config["Jwt:ExpiresInMinutes"] ?? "1440")),
                signingCredentials: new SigningCredentials(key, SecurityAlgorithms.HmacSha256));
            return new JwtSecurityTokenHandler().WriteToken(token);
        }

        private string CreateRefreshToken(string userName)
        {
            var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(
                _config["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured.")));
            var claims = new[]
            {
                new Claim(ClaimTypes.Name, userName),
                new Claim("Purpose", "refresh"),
                new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
            };
            var token = new JwtSecurityToken(_config["Jwt:Issuer"], _config["Jwt:Audience"], claims,
                expires: DateTime.UtcNow.AddDays(7),
                signingCredentials: new SigningCredentials(key, SecurityAlgorithms.HmacSha256));
            return new JwtSecurityTokenHandler().WriteToken(token);
        }

        private string CreateResetToken(string userName)
        {
            var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(
                _config["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured.")));
            var claims = new[]
            {
                new Claim(ClaimTypes.Name, userName),
                new Claim("Purpose", "reset"),
                new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
            };
            var token = new JwtSecurityToken(_config["Jwt:Issuer"], _config["Jwt:Audience"], claims,
                expires: DateTime.UtcNow.AddMinutes(15),
                signingCredentials: new SigningCredentials(key, SecurityAlgorithms.HmacSha256));
            return new JwtSecurityTokenHandler().WriteToken(token);
        }

        private string CreateGuestToken(string guestUserName, string displayName, string meetingId)
        {
            var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(
                _config["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured.")));
            var claims = new[]
            {
                new Claim(ClaimTypes.Name, guestUserName),
                new Claim("FullName", displayName),
                new Claim("IsGuest", "true"),
                new Claim("MeetingId", meetingId),
                new Claim("TokenVersion", "1"),
                new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
            };
            var token = new JwtSecurityToken(_config["Jwt:Issuer"], _config["Jwt:Audience"], claims,
                expires: DateTime.UtcNow.AddHours(4),
                signingCredentials: new SigningCredentials(key, SecurityAlgorithms.HmacSha256));
            return new JwtSecurityTokenHandler().WriteToken(token);
        }

        private ClaimsPrincipal? ValidateToken(string token, string expectedPurpose)
        {
            var tokenHandler = new JwtSecurityTokenHandler();
            var key = Encoding.UTF8.GetBytes(_config["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured."));
            try
            {
                var principal = tokenHandler.ValidateToken(token, new TokenValidationParameters
                {
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = new SymmetricSecurityKey(key),
                    ValidateIssuer = true,
                    ValidIssuer = _config["Jwt:Issuer"],
                    ValidateAudience = true,
                    ValidAudience = _config["Jwt:Audience"],
                    ValidateLifetime = true,
                    ClockSkew = TimeSpan.Zero
                }, out SecurityToken validatedToken);

                var jwtToken = (JwtSecurityToken)validatedToken;
                var purpose = principal.FindFirst("Purpose")?.Value;
                if (purpose != expectedPurpose) return null;

                return principal;
            }
            catch
            {
                return null;
            }
        }
    }
}
