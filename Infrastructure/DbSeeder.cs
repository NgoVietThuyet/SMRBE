using BE.Core.Entities.AD;
using BE.Core.Entities.MD;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using System;
using System.Threading.Tasks;

namespace BE.Infrastructure
{
    /// <summary>
    /// Tiện ích khởi tạo dữ liệu mẫu mặc định cho cơ sở dữ liệu (DbSeeder).
    /// </summary>
    public static class DbSeeder
    {
        /// <summary>
        /// Thực hiện gieo dữ liệu ban đầu cho các bảng danh mục và tài khoản ADMIN mặc định.
        /// </summary>
        public static async Task SeedAsync(IServiceProvider services)
        {
            using var scope = services.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            var now = DateTime.UtcNow;

            for (var attempt = 1; attempt <= 3; attempt++)
            {
                try
                {
                    // Khởi tạo phòng ban gốc ROOT nếu chưa tồn tại
                    if (!await db.MdOrganizes.AnyAsync(x => x.Id == "ROOT"))
                    {
                        db.MdOrganizes.Add(new MdOrganize
                        {
                            Id = "ROOT",
                            PId = string.Empty,
                            Name = "Công ty Smart Meeting",
                            OrderNumber = 1,
                            Expanded = true,
                            IsActive = true,
                            Notes = "Đơn vị gốc",
                            CreateBy = "system",
                            CreateDate = now,
                            UpdateBy = "system",
                            UpdateDate = now
                        });
                    }

                    // Khởi tạo chức danh quản trị mặc định ADMIN nếu chưa tồn tại
                    if (!await db.MdTitles.AnyAsync(x => x.Code == "ADMIN"))
                    {
                        db.MdTitles.Add(new MdTitle
                        {
                            Code = "ADMIN",
                            Name = "Quản trị hệ thống",
                            Notes = "Chức danh quản trị",
                            OrderNumber = 1,
                            IsActive = true,
                            CreateBy = "system",
                            CreateDate = now,
                            UpdateBy = "system",
                            UpdateDate = now
                        });
                    }

                    // Tìm kiếm tài khoản admin mặc định
                    var admin = await db.AdAccounts.FirstOrDefaultAsync(x => x.UserName == "admin" || x.Email == "admin@gmail.com");
                    if (admin == null)
                    {
                        // Nếu chưa tồn tại, tạo mới tài khoản admin với mật khẩu mặc định "admin2123"
                        db.AdAccounts.Add(new AdAccount
                        {
                            UserName = "admin",
                            Password = BCrypt.Net.BCrypt.HashPassword("admin2123"),
                            FullName = "Quản trị viên",
                            Email = "admin@gmail.com",
                            Phone = string.Empty,
                            Address = string.Empty,
                            OrgId = "ROOT",
                            TitleCode = "ADMIN",
                            RoleCodes = "[\"SUPER_ADMIN\"]",
                            IsActive = true,
                            MustChangePassword = false,
                            TokenVersion = 1,
                            CreateBy = "system",
                            CreateDate = now,
                            UpdateBy = "system",
                            UpdateDate = now
                        });
                    }
                    else
                    {
                        // Nếu đã tồn tại, cập nhật lại mật khẩu thành "admin2123" để đảm bảo khả năng truy nhập
                        admin.Password = BCrypt.Net.BCrypt.HashPassword("admin2123");
                        admin.MustChangePassword = false;
                        admin.UpdateBy = "system";
                        admin.UpdateDate = now;
                    }

                    await db.SaveChangesAsync();
                    return;
                }
                catch (InvalidOperationException) when (attempt < 3)
                {
                    await Task.Delay(TimeSpan.FromSeconds(attempt));
                    db.ChangeTracker.Clear();
                }
            }
        }
    }
}
