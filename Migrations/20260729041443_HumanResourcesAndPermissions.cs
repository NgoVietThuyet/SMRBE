using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BE.Migrations
{
    /// <inheritdoc />
    public partial class HumanResourcesAndPermissions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "MdTitles",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<string>(
                name: "PermissionJson",
                table: "MdTitles",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AlterColumn<string>(
                name: "PId",
                table: "MdOrganizes",
                type: "nvarchar(450)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)");

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "MdOrganizes",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<string>(
                name: "Notes",
                table: "MdOrganizes",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "PermissionJson",
                table: "MdOrganizes",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AlterColumn<string>(
                name: "TitleCode",
                table: "AdAccounts",
                type: "nvarchar(450)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)");

            migrationBuilder.AlterColumn<string>(
                name: "OrgId",
                table: "AdAccounts",
                type: "nvarchar(450)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)");

            migrationBuilder.AlterColumn<string>(
                name: "Email",
                table: "AdAccounts",
                type: "nvarchar(255)",
                maxLength: 255,
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)");

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "AdAccounts",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<DateTime>(
                name: "LastLoginAt",
                table: "AdAccounts",
                type: "datetime2",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "MustChangePassword",
                table: "AdAccounts",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<string>(
                name: "PermissionJson",
                table: "AdAccounts",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "RoleCodes",
                table: "AdAccounts",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<int>(
                name: "TokenVersion",
                table: "AdAccounts",
                type: "int",
                nullable: false,
                defaultValue: 1);

            // Prepare required reference rows before adding the new foreign keys.
            // This also upgrades legacy accounts that were created with empty OrgId/TitleCode.
            migrationBuilder.Sql("""
                IF NOT EXISTS (SELECT 1 FROM [MdOrganizes] WHERE [Id] = N'ROOT')
                BEGIN
                    INSERT INTO [MdOrganizes]
                        ([Id], [PId], [Name], [OrderNumber], [Expanded], [PermissionJson], [IsActive], [Notes], [CreateBy], [CreateDate], [UpdateBy], [UpdateDate])
                    VALUES
                        (N'ROOT', N'', N'Công ty Smart Meeting', 1, 1, NULL, 1, N'Đơn vị gốc', N'system', SYSUTCDATETIME(), N'system', SYSUTCDATETIME());
                END;

                IF NOT EXISTS (SELECT 1 FROM [MdTitles] WHERE [Code] = N'ADMIN')
                BEGIN
                    INSERT INTO [MdTitles]
                        ([Code], [Name], [Notes], [OrderNumber], [PermissionJson], [IsActive], [CreateBy], [CreateDate], [UpdateBy], [UpdateDate])
                    VALUES
                        (N'ADMIN', N'Quản trị hệ thống', N'Chức danh quản trị', 1, NULL, 1, N'system', SYSUTCDATETIME(), N'system', SYSUTCDATETIME());
                END;

                UPDATE a SET [OrgId] = N'ROOT'
                FROM [AdAccounts] a
                WHERE NULLIF(LTRIM(RTRIM(a.[OrgId])), N'') IS NULL
                   OR NOT EXISTS (SELECT 1 FROM [MdOrganizes] o WHERE o.[Id] = a.[OrgId]);

                UPDATE a SET [TitleCode] = N'ADMIN'
                FROM [AdAccounts] a
                WHERE NULLIF(LTRIM(RTRIM(a.[TitleCode])), N'') IS NULL
                   OR NOT EXISTS (SELECT 1 FROM [MdTitles] t WHERE t.[Code] = a.[TitleCode]);
                """);

            migrationBuilder.CreateIndex(
                name: "IX_MdOrganizes_PId",
                table: "MdOrganizes",
                column: "PId");

            migrationBuilder.CreateIndex(
                name: "IX_AdAccounts_Email",
                table: "AdAccounts",
                column: "Email",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_AdAccounts_OrgId",
                table: "AdAccounts",
                column: "OrgId");

            migrationBuilder.CreateIndex(
                name: "IX_AdAccounts_TitleCode",
                table: "AdAccounts",
                column: "TitleCode");

            migrationBuilder.AddForeignKey(
                name: "FK_AdAccounts_MdOrganizes_OrgId",
                table: "AdAccounts",
                column: "OrgId",
                principalTable: "MdOrganizes",
                principalColumn: "Id",
                onDelete: ReferentialAction.Restrict);

            migrationBuilder.AddForeignKey(
                name: "FK_AdAccounts_MdTitles_TitleCode",
                table: "AdAccounts",
                column: "TitleCode",
                principalTable: "MdTitles",
                principalColumn: "Code",
                onDelete: ReferentialAction.Restrict);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_AdAccounts_MdOrganizes_OrgId",
                table: "AdAccounts");

            migrationBuilder.DropForeignKey(
                name: "FK_AdAccounts_MdTitles_TitleCode",
                table: "AdAccounts");

            migrationBuilder.DropIndex(
                name: "IX_MdOrganizes_PId",
                table: "MdOrganizes");

            migrationBuilder.DropIndex(
                name: "IX_AdAccounts_Email",
                table: "AdAccounts");

            migrationBuilder.DropIndex(
                name: "IX_AdAccounts_OrgId",
                table: "AdAccounts");

            migrationBuilder.DropIndex(
                name: "IX_AdAccounts_TitleCode",
                table: "AdAccounts");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "MdTitles");

            migrationBuilder.DropColumn(
                name: "PermissionJson",
                table: "MdTitles");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "MdOrganizes");

            migrationBuilder.DropColumn(
                name: "Notes",
                table: "MdOrganizes");

            migrationBuilder.DropColumn(
                name: "PermissionJson",
                table: "MdOrganizes");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "AdAccounts");

            migrationBuilder.DropColumn(
                name: "LastLoginAt",
                table: "AdAccounts");

            migrationBuilder.DropColumn(
                name: "MustChangePassword",
                table: "AdAccounts");

            migrationBuilder.DropColumn(
                name: "PermissionJson",
                table: "AdAccounts");

            migrationBuilder.DropColumn(
                name: "RoleCodes",
                table: "AdAccounts");

            migrationBuilder.DropColumn(
                name: "TokenVersion",
                table: "AdAccounts");

            migrationBuilder.AlterColumn<string>(
                name: "PId",
                table: "MdOrganizes",
                type: "nvarchar(max)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(450)");

            migrationBuilder.AlterColumn<string>(
                name: "TitleCode",
                table: "AdAccounts",
                type: "nvarchar(max)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(450)");

            migrationBuilder.AlterColumn<string>(
                name: "OrgId",
                table: "AdAccounts",
                type: "nvarchar(max)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(450)");

            migrationBuilder.AlterColumn<string>(
                name: "Email",
                table: "AdAccounts",
                type: "nvarchar(max)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(255)",
                oldMaxLength: 255);

        }
    }
}
