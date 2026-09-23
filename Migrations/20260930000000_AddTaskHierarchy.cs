using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BE.Migrations
{
    /// <inheritdoc />
    public partial class AddTaskHierarchy : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "ParentId",
                table: "MeetingTasks",
                type: "nvarchar(450)",
                nullable: true);

            migrationBuilder.AddColumn<int>(
                name: "Level",
                table: "MeetingTasks",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.CreateIndex(
                name: "IX_MeetingTasks_ParentId",
                table: "MeetingTasks",
                column: "ParentId");

            migrationBuilder.CreateIndex(
                name: "IX_MeetingTasks_Level",
                table: "MeetingTasks",
                column: "Level");

            migrationBuilder.AddForeignKey(
                name: "FK_MeetingTasks_MeetingTasks_ParentId",
                table: "MeetingTasks",
                column: "ParentId",
                principalTable: "MeetingTasks",
                principalColumn: "Id",
                onDelete: ReferentialAction.Restrict);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_MeetingTasks_MeetingTasks_ParentId",
                table: "MeetingTasks");

            migrationBuilder.DropIndex(
                name: "IX_MeetingTasks_ParentId",
                table: "MeetingTasks");

            migrationBuilder.DropIndex(
                name: "IX_MeetingTasks_Level",
                table: "MeetingTasks");

            migrationBuilder.DropColumn(
                name: "ParentId",
                table: "MeetingTasks");

            migrationBuilder.DropColumn(
                name: "Level",
                table: "MeetingTasks");
        }
    }
}
