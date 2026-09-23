using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BE.Migrations
{
    /// <inheritdoc />
    public partial class AddMeetingTasks : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "MeetingTasks",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    MeetingId = table.Column<string>(type: "nvarchar(450)", nullable: true),
                    Title = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: false),
                    Description = table.Column<string>(type: "nvarchar(4000)", maxLength: 4000, nullable: true),
                    AssigneeUserName = table.Column<string>(type: "nvarchar(450)", nullable: true),
                    DueDate = table.Column<DateTime>(type: "datetime2", nullable: true),
                    Status = table.Column<int>(type: "int", nullable: false, defaultValue: 0),
                    Priority = table.Column<int>(type: "int", nullable: false, defaultValue: 1),
                    IsPublic = table.Column<bool>(type: "bit", nullable: false, defaultValue: false),
                    CreateBy = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    CreateDate = table.Column<DateTime>(type: "datetime2", nullable: true),
                    UpdateBy = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    UpdateDate = table.Column<DateTime>(type: "datetime2", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_MeetingTasks", x => x.Id);
                    table.ForeignKey(
                        name: "FK_MeetingTasks_MeetingInfos_MeetingId",
                        column: x => x.MeetingId,
                        principalTable: "MeetingInfos",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "MeetingTaskShares",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    TaskId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    UserName = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Permission = table.Column<int>(type: "int", nullable: false),
                    CreateBy = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    CreateDate = table.Column<DateTime>(type: "datetime2", nullable: true),
                    UpdateBy = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    UpdateDate = table.Column<DateTime>(type: "datetime2", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_MeetingTaskShares", x => x.Id);
                    table.ForeignKey(
                        name: "FK_MeetingTaskShares_MeetingTasks_TaskId",
                        column: x => x.TaskId,
                        principalTable: "MeetingTasks",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_MeetingTasks_AssigneeUserName",
                table: "MeetingTasks",
                column: "AssigneeUserName");

            migrationBuilder.CreateIndex(
                name: "IX_MeetingTasks_DueDate",
                table: "MeetingTasks",
                column: "DueDate");

            migrationBuilder.CreateIndex(
                name: "IX_MeetingTasks_MeetingId",
                table: "MeetingTasks",
                column: "MeetingId");

            migrationBuilder.CreateIndex(
                name: "IX_MeetingTaskShares_TaskId_UserName",
                table: "MeetingTaskShares",
                columns: new[] { "TaskId", "UserName" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "MeetingTaskShares");

            migrationBuilder.DropTable(
                name: "MeetingTasks");
        }
    }
}
