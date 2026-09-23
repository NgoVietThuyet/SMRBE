// Tầng truy cập dữ liệu được hiện thực hóa qua lớp trung tâm AppDbContext, chứa các thuộc tính DbSet<> đại diện cho các bảng.
using Microsoft.EntityFrameworkCore;
using BE.Core.Entities;
using BE.Core.Entities.AD;
using BE.Core.Entities.MD;
using BE.Core.Entities.MT;
using BE.Core.Entities.CF;

namespace BE.Infrastructure
{
    public class AppDbContext : DbContext
    {
        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

        public DbSet<MdOrganize> MdOrganizes { get; set; }
        public DbSet<MdTitle> MdTitles { get; set; }
        public DbSet<AdAccount> AdAccounts { get; set; }
        public DbSet<MeetingInfo> MeetingInfos { get; set; }
        public DbSet<MeetingPersonal> MeetingPersonals { get; set; }
        public DbSet<MeetingMessage> MeetingMessages { get; set; }
        public DbSet<MeetingAuditLog> MeetingAuditLogs { get; set; }
        public DbSet<MeetingTask> MeetingTasks { get; set; }
        public DbSet<MeetingTaskShare> MeetingTaskShares { get; set; }
        public DbSet<CmFile> CmFiles { get; set; }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<MdOrganize>().HasKey(e => e.Id);
            modelBuilder.Entity<MdTitle>().HasKey(e => e.Code);
            modelBuilder.Entity<AdAccount>().HasKey(e => e.UserName);
            modelBuilder.Entity<AdAccount>().Property(e => e.Email).HasMaxLength(255);
            modelBuilder.Entity<AdAccount>().HasIndex(e => e.Email).IsUnique();
            modelBuilder.Entity<AdAccount>().HasIndex(e => e.OrgId);
            modelBuilder.Entity<AdAccount>().HasIndex(e => e.TitleCode);
            modelBuilder.Entity<MdOrganize>().HasIndex(e => e.PId);
            modelBuilder.Entity<AdAccount>().HasOne<MdOrganize>().WithMany().HasForeignKey(e => e.OrgId).OnDelete(DeleteBehavior.Restrict);
            modelBuilder.Entity<AdAccount>().HasOne<MdTitle>().WithMany().HasForeignKey(e => e.TitleCode).OnDelete(DeleteBehavior.Restrict);
            modelBuilder.Entity<MeetingInfo>().HasKey(e => e.Id);
            modelBuilder.Entity<MeetingInfo>().Property(e => e.RowVersion).IsRowVersion();
            modelBuilder.Entity<MeetingInfo>().HasIndex(e => e.RoomCode).IsUnique();
            modelBuilder.Entity<MeetingInfo>().HasIndex(e => new { e.Status, e.ExpectedStartTime });
            modelBuilder.Entity<MeetingPersonal>().HasKey(e => e.Id);
            modelBuilder.Entity<MeetingPersonal>().HasIndex(e => new { e.MeetingId, e.UserName }).IsUnique();
            modelBuilder.Entity<MeetingPersonal>().HasOne<MeetingInfo>().WithMany().HasForeignKey(e => e.MeetingId).OnDelete(DeleteBehavior.Cascade);
            modelBuilder.Entity<MeetingMessage>().HasKey(e => e.Id);
            modelBuilder.Entity<MeetingAuditLog>().HasKey(e => e.Id);
            modelBuilder.Entity<MeetingAuditLog>().HasIndex(e => new { e.MeetingId, e.OccurredAt });
            modelBuilder.Entity<MeetingAuditLog>().HasOne<MeetingInfo>().WithMany().HasForeignKey(e => e.MeetingId).OnDelete(DeleteBehavior.Cascade);
            modelBuilder.Entity<MeetingTask>().HasKey(e => e.Id);
            modelBuilder.Entity<MeetingTask>().Property(e => e.Title).HasMaxLength(300);
            modelBuilder.Entity<MeetingTask>().Property(e => e.Description).HasMaxLength(4000);
            modelBuilder.Entity<MeetingTask>().Property(e => e.Status).HasDefaultValue(0);
            modelBuilder.Entity<MeetingTask>().Property(e => e.Priority).HasDefaultValue(1);
            modelBuilder.Entity<MeetingTask>().Property(e => e.IsPublic).HasDefaultValue(false);
            modelBuilder.Entity<MeetingTask>().HasIndex(e => e.MeetingId);
            modelBuilder.Entity<MeetingTask>().HasIndex(e => e.AssigneeUserName);
            modelBuilder.Entity<MeetingTask>().HasIndex(e => e.DueDate);
            modelBuilder.Entity<MeetingTask>().HasIndex(e => e.ParentId);
            modelBuilder.Entity<MeetingTask>().HasIndex(e => e.Level);
            modelBuilder.Entity<MeetingTask>().HasOne(e => e.Meeting).WithMany().HasForeignKey(e => e.MeetingId).OnDelete(DeleteBehavior.Cascade);
            modelBuilder.Entity<MeetingTask>().HasOne(e => e.Parent).WithMany(e => e.Children).HasForeignKey(e => e.ParentId).OnDelete(DeleteBehavior.Restrict);
            modelBuilder.Entity<MeetingTaskShare>().HasKey(e => e.Id);
            modelBuilder.Entity<MeetingTaskShare>().HasIndex(e => new { e.TaskId, e.UserName }).IsUnique();
            // Gắn vào navigation e.Task (không dùng HasOne<MeetingTask>() rỗng vì sẽ tạo quan hệ thứ hai tách khỏi property TaskId).
            modelBuilder.Entity<MeetingTaskShare>().HasOne(e => e.Task).WithMany().HasForeignKey(e => e.TaskId).OnDelete(DeleteBehavior.Cascade);
            modelBuilder.Entity<CmFile>().HasKey(e => e.Id);
            modelBuilder.Entity<CmFile>().Property(e => e.FileSize).HasColumnType("decimal(18,2)");
        }
    }
}
