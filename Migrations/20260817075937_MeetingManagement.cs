using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BE.Migrations
{
    /// <inheritdoc />
    public partial class MeetingManagement : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AlterColumn<string>(
                name: "UserName",
                table: "MeetingPersonals",
                type: "nvarchar(450)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)");

            migrationBuilder.AlterColumn<string>(
                name: "MeetingId",
                table: "MeetingPersonals",
                type: "nvarchar(450)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)");

            migrationBuilder.AddColumn<string>(
                name: "Agenda",
                table: "MeetingInfos",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "CancellationReason",
                table: "MeetingInfos",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<DateTime>(
                name: "ExpectedEndTime",
                table: "MeetingInfos",
                type: "datetime2",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsArchived",
                table: "MeetingInfos",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "IsDeleted",
                table: "MeetingInfos",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "IsDraft",
                table: "MeetingInfos",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<string>(
                name: "JoinUrl",
                table: "MeetingInfos",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "RoomCode",
                table: "MeetingInfos",
                type: "nvarchar(450)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<byte[]>(
                name: "RowVersion",
                table: "MeetingInfos",
                type: "rowversion",
                rowVersion: true,
                nullable: false,
                defaultValue: new byte[0]);

            migrationBuilder.AddColumn<string>(
                name: "SettingsJson",
                table: "MeetingInfos",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "TimeZone",
                table: "MeetingInfos",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<int>(
                name: "Version",
                table: "MeetingInfos",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "Visibility",
                table: "MeetingInfos",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.CreateTable(
                name: "MeetingAuditLogs",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    MeetingId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Action = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    ActorId = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    OccurredAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    CorrelationId = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Version = table.Column<int>(type: "int", nullable: false),
                    PayloadJson = table.Column<string>(type: "nvarchar(max)", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_MeetingAuditLogs", x => x.Id);
                    table.ForeignKey(
                        name: "FK_MeetingAuditLogs_MeetingInfos_MeetingId",
                        column: x => x.MeetingId,
                        principalTable: "MeetingInfos",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            // Backfill existing meetings before the unique room-code index is created.
            migrationBuilder.Sql("UPDATE MeetingInfos SET RoomCode = Id, JoinUrl = '/meet/' + Id, SettingsJson = '{\"schemaVersion\":1,\"chatEnabled\":true,\"screenShareEnabled\":true,\"whiteboardEnabled\":true,\"fileUploadEnabled\":true,\"recordingEnabled\":true,\"aiMinutesEnabled\":true}', TimeZone = 'Asia/Bangkok', Version = 1 WHERE RoomCode = ''");

            migrationBuilder.CreateIndex(
                name: "IX_MeetingPersonals_MeetingId_UserName",
                table: "MeetingPersonals",
                columns: new[] { "MeetingId", "UserName" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_MeetingInfos_RoomCode",
                table: "MeetingInfos",
                column: "RoomCode",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_MeetingInfos_Status_ExpectedStartTime",
                table: "MeetingInfos",
                columns: new[] { "Status", "ExpectedStartTime" });

            migrationBuilder.CreateIndex(
                name: "IX_MeetingAuditLogs_MeetingId_OccurredAt",
                table: "MeetingAuditLogs",
                columns: new[] { "MeetingId", "OccurredAt" });

            migrationBuilder.AddForeignKey(
                name: "FK_MeetingPersonals_MeetingInfos_MeetingId",
                table: "MeetingPersonals",
                column: "MeetingId",
                principalTable: "MeetingInfos",
                principalColumn: "Id",
                onDelete: ReferentialAction.Cascade);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_MeetingPersonals_MeetingInfos_MeetingId",
                table: "MeetingPersonals");

            migrationBuilder.DropTable(
                name: "MeetingAuditLogs");

            migrationBuilder.DropIndex(
                name: "IX_MeetingPersonals_MeetingId_UserName",
                table: "MeetingPersonals");

            migrationBuilder.DropIndex(
                name: "IX_MeetingInfos_RoomCode",
                table: "MeetingInfos");

            migrationBuilder.DropIndex(
                name: "IX_MeetingInfos_Status_ExpectedStartTime",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "Agenda",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "CancellationReason",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "ExpectedEndTime",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "IsArchived",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "IsDeleted",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "IsDraft",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "JoinUrl",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "RoomCode",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "RowVersion",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "SettingsJson",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "TimeZone",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "Version",
                table: "MeetingInfos");

            migrationBuilder.DropColumn(
                name: "Visibility",
                table: "MeetingInfos");

            migrationBuilder.AlterColumn<string>(
                name: "UserName",
                table: "MeetingPersonals",
                type: "nvarchar(max)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(450)");

            migrationBuilder.AlterColumn<string>(
                name: "MeetingId",
                table: "MeetingPersonals",
                type: "nvarchar(max)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(450)");
        }
    }
}
