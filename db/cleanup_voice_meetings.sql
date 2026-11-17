-- =============================================================================
-- Xóa cứng toàn bộ 6 phiên họp Voice Simulation khỏi SQL Server (KLTNSMR)
-- Chạy trên máy chủ (nơi kết nối được sso.d2s.com.vn:2025):
--   sqlcmd -S sso.d2s.com.vn,2025 -d KLTNSMR -U <DB_USERNAME> -P <DB_PASSWORD> -C -i cleanup_voice_meetings.sql
-- =============================================================================

SET NOCOUNT ON;
BEGIN TRANSACTION;

DECLARE @MeetingIds TABLE (Id NVARCHAR(100) PRIMARY KEY);
INSERT INTO @MeetingIds (Id) VALUES
    (N'48829592d4b64036b6528f3714f1eddf'), -- voice_incident
    (N'5f2e1b624d3e4739a90824a68b245e25'), -- voice_security
    (N'6a45e63defca47c99cf5c35ad3ebabf9'), -- voice_budget
    (N'0af5f2fa611e4d16b0d96a06a789670b'), -- voice_sprint14
    (N'4e250f0dfa31477b908b7aa1ce811e3e'), -- voice_anphat
    (N'7740886532054afb932bac3a40f6a896'); -- voice_workshop

DECLARE @RefIds TABLE (RefId NVARCHAR(100) PRIMARY KEY);
INSERT INTO @RefIds (RefId)
SELECT DISTINCT ReferenceFileId
FROM MeetingInfos
WHERE Id IN (SELECT Id FROM @MeetingIds) AND ReferenceFileId IS NOT NULL;

DECLARE @TaskIds TABLE (Id NVARCHAR(100) PRIMARY KEY);
INSERT INTO @TaskIds (Id)
SELECT Id FROM MeetingTasks WHERE MeetingId IN (SELECT Id FROM @MeetingIds);

-- 1. Xóa dữ liệu công việc liên quan (nếu còn sót)
DELETE FROM MtTaskCardLabels WHERE TaskId IN (SELECT Id FROM @TaskIds);
DELETE FROM MtTaskChecklistItems WHERE TaskId IN (SELECT Id FROM @TaskIds);
DELETE FROM MtTaskComments WHERE TaskId IN (SELECT Id FROM @TaskIds);
DELETE FROM MtTaskLabels WHERE WorkId IN (SELECT Id FROM @TaskIds);
DELETE FROM MeetingTaskShares WHERE TaskId IN (SELECT Id FROM @TaskIds);
DELETE FROM Notifications WHERE ResourceId IN (SELECT Id FROM @TaskIds);

DELETE FROM MeetingTasks WHERE Id IN (SELECT Id FROM @TaskIds) AND [Level] = 3;
DELETE FROM MeetingTasks WHERE Id IN (SELECT Id FROM @TaskIds) AND [Level] = 2;
DELETE FROM MeetingTasks WHERE Id IN (SELECT Id FROM @TaskIds) AND [Level] = 1;
DELETE FROM MeetingTasks WHERE Id IN (SELECT Id FROM @TaskIds) AND [Level] = 0;

-- 2. Xóa tệp đính kèm / bản ghi / mẫu trong CmFiles
DELETE FROM CmFiles
WHERE ReferenceFileId IN (SELECT Id FROM @MeetingIds)
   OR ReferenceFileId IN (SELECT RefId FROM @RefIds)
   OR ReferenceFileId IN (SELECT Id FROM @TaskIds)
   OR Id IN (SELECT ResultRef FROM SyBackgroundJobs WHERE RefId IN (SELECT Id FROM @MeetingIds) AND ResultRef IS NOT NULL);

-- 3. Xóa dữ liệu họp, transcript, biên bản AI, sự kiện, chat, thông báo, audit
DELETE FROM MtMinutes WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MtMeetingRecords WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM SyBackgroundJobs WHERE RefId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MeetingTranscriptSegments WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MtAttendanceEvents WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MeetingAuditLogs WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MtLobbyRequests WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MeetingMessages WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MtWhiteboards WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM Notifications WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM SyAuditLogs WHERE TargetId IN (SELECT Id FROM @MeetingIds);

IF OBJECT_ID('dbo.MtMeetingRagChunks', 'U') IS NOT NULL
    DELETE FROM MtMeetingRagChunks WHERE MeetingId IN (SELECT Id FROM @MeetingIds);

-- 4. Xóa người tham gia và cuộc họp
DELETE FROM MeetingPersonals WHERE MeetingId IN (SELECT Id FROM @MeetingIds);
DELETE FROM MeetingInfos WHERE Id IN (SELECT Id FROM @MeetingIds);

COMMIT TRANSACTION;
PRINT N'Đã xóa cứng hoàn toàn 6 phiên họp Voice Simulation khỏi KLTNSMR.';
