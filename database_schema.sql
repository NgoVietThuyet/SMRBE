CREATE TABLE [AdAccounts] (
    [UserName] nvarchar(450) NOT NULL,
    [Password] nvarchar(max) NOT NULL,
    [FullName] nvarchar(max) NOT NULL,
    [Phone] nvarchar(max) NOT NULL,
    [Email] nvarchar(max) NOT NULL,
    [Address] nvarchar(max) NOT NULL,
    [OrgId] nvarchar(max) NOT NULL,
    [TitleCode] nvarchar(max) NOT NULL,
    [CreateBy] nvarchar(max) NOT NULL,
    [CreateDate] datetime2 NULL,
    [UpdateBy] nvarchar(max) NOT NULL,
    [UpdateDate] datetime2 NULL,
    CONSTRAINT [PK_AdAccounts] PRIMARY KEY ([UserName])
);
GO


CREATE TABLE [CmFiles] (
    [Id] nvarchar(450) NOT NULL,
    [FileName] nvarchar(max) NOT NULL,
    [FileSize] decimal(18,2) NOT NULL,
    [MimeType] nvarchar(max) NOT NULL,
    [Extention] nvarchar(max) NOT NULL,
    [Type] int NOT NULL,
    [Icon] nvarchar(max) NOT NULL,
    [RefrenceFileId] nvarchar(max) NOT NULL,
    [IsBienBan] bit NOT NULL,
    [OrderNumber] int NOT NULL,
    [VoiceToText] nvarchar(max) NOT NULL,
    [CreateBy] nvarchar(max) NOT NULL,
    [CreateDate] datetime2 NULL,
    [UpdateBy] nvarchar(max) NOT NULL,
    [UpdateDate] datetime2 NULL,
    CONSTRAINT [PK_CmFiles] PRIMARY KEY ([Id])
);
GO


CREATE TABLE [MdOrganizes] (
    [Id] nvarchar(450) NOT NULL,
    [PId] nvarchar(max) NOT NULL,
    [Name] nvarchar(max) NOT NULL,
    [OrderNumber] int NOT NULL,
    [Expanded] bit NOT NULL,
    [CreateBy] nvarchar(max) NOT NULL,
    [CreateDate] datetime2 NULL,
    [UpdateBy] nvarchar(max) NOT NULL,
    [UpdateDate] datetime2 NULL,
    CONSTRAINT [PK_MdOrganizes] PRIMARY KEY ([Id])
);
GO


CREATE TABLE [MdTitles] (
    [Code] nvarchar(450) NOT NULL,
    [Name] nvarchar(max) NOT NULL,
    [Notes] nvarchar(max) NOT NULL,
    [OrderNumber] int NOT NULL,
    [CreateBy] nvarchar(max) NOT NULL,
    [CreateDate] datetime2 NULL,
    [UpdateBy] nvarchar(max) NOT NULL,
    [UpdateDate] datetime2 NULL,
    CONSTRAINT [PK_MdTitles] PRIMARY KEY ([Code])
);
GO


CREATE TABLE [MeetingInfos] (
    [Id] nvarchar(450) NOT NULL,
    [Name] nvarchar(max) NOT NULL,
    [ExpectedStartTime] datetime2 NOT NULL,
    [StartDate] datetime2 NULL,
    [EndDate] datetime2 NULL,
    [MeetContent] nvarchar(max) NOT NULL,
    [Status] int NOT NULL,
    [Notes] nvarchar(max) NOT NULL,
    [RefrenceFileId] nvarchar(max) NOT NULL,
    [CreateBy] nvarchar(max) NOT NULL,
    [CreateDate] datetime2 NULL,
    [UpdateBy] nvarchar(max) NOT NULL,
    [UpdateDate] datetime2 NULL,
    CONSTRAINT [PK_MeetingInfos] PRIMARY KEY ([Id])
);
GO


CREATE TABLE [MeetingMessages] (
    [Id] nvarchar(450) NOT NULL,
    [MeetingId] nvarchar(max) NOT NULL,
    [SenderUserId] nvarchar(max) NOT NULL,
    [ReceiverUserId] nvarchar(max) NOT NULL,
    [MessageText] nvarchar(max) NOT NULL,
    [CreateBy] nvarchar(max) NOT NULL,
    [CreateDate] datetime2 NULL,
    [UpdateBy] nvarchar(max) NOT NULL,
    [UpdateDate] datetime2 NULL,
    CONSTRAINT [PK_MeetingMessages] PRIMARY KEY ([Id])
);
GO


CREATE TABLE [MeetingPersonals] (
    [Id] nvarchar(450) NOT NULL,
    [MeetingId] nvarchar(max) NOT NULL,
    [UserName] nvarchar(max) NOT NULL,
    [FullName] nvarchar(max) NOT NULL,
    [Phone] nvarchar(max) NOT NULL,
    [Email] nvarchar(max) NOT NULL,
    [Address] nvarchar(max) NOT NULL,
    [OrgId] nvarchar(max) NOT NULL,
    [TitleCode] nvarchar(max) NOT NULL,
    [RefrenceFileId] nvarchar(max) NOT NULL,
    [Type] int NOT NULL,
    [IsChuTri] bit NOT NULL,
    [IsJoined] bit NOT NULL,
    [JoinTime] datetime2 NULL,
    [CreateBy] nvarchar(max) NOT NULL,
    [CreateDate] datetime2 NULL,
    [UpdateBy] nvarchar(max) NOT NULL,
    [UpdateDate] datetime2 NULL,
    CONSTRAINT [PK_MeetingPersonals] PRIMARY KEY ([Id])
);
GO


