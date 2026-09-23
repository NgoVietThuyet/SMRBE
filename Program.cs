using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using System.Text;
using BE.Infrastructure;
using BE.Core.Data;
using BE.Service.Services;
using BE.API.Authorization;
using BE.API.Hubs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "Nhập token bằng mẫu: Bearer {token của bạn}",
        Name = "Authorization",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.ApiKey,
        Scheme = "Bearer"
    });

    c.AddSecurityRequirement(new Microsoft.OpenApi.Models.OpenApiSecurityRequirement
    {
        {
            new Microsoft.OpenApi.Models.OpenApiSecurityScheme
            {
                Reference = new Microsoft.OpenApi.Models.OpenApiReference
                {
                    Type = Microsoft.OpenApi.Models.ReferenceType.SecurityScheme,
                    Id = "Bearer"
                }
            },
            Array.Empty<string>()
        }
    });
});
builder.Services.AddSignalR();

// Database
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseSqlServer(
        builder.Configuration.GetConnectionString("DefaultConnection"),
        sql => sql.EnableRetryOnFailure()));

// Application Services
builder.Services.AddScoped<IMeetingService, MeetingService>();
builder.Services.AddScoped<ITaskService, TaskService>();
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddSingleton<IObjectStorageService, MinioObjectStorageService>();
builder.Services.AddScoped<IPermissionService, PermissionService>();
builder.Services.AddScoped<HumanResourceService>();
builder.Services.AddSingleton<IAuthorizationPolicyProvider, PermissionPolicyProvider>();
builder.Services.AddScoped<IAuthorizationHandler, PermissionHandler>();

// JWT Authentication
var jwtKey = builder.Configuration["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured.");
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = builder.Configuration["Jwt:Issuer"],
            ValidAudience = builder.Configuration["Jwt:Audience"],
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey))
        };
        options.Events = new JwtBearerEvents
        {
            OnMessageReceived = context =>
            {
                var accessToken = context.Request.Query["access_token"];
                var path = context.HttpContext.Request.Path;
                if (!string.IsNullOrEmpty(accessToken) && path.StartsWithSegments("/meetinghub"))
                {
                    context.Token = accessToken;
                }
                return Task.CompletedTask;
            },
            OnTokenValidated = async context =>
            {
                var principal = context.Principal;
                var isGuest = principal?.FindFirst("IsGuest")?.Value == "true";
                if (isGuest)
                {
                    var meetingId = principal?.FindFirst("MeetingId")?.Value;
                    var dbMeeting = context.HttpContext.RequestServices.GetRequiredService<AppDbContext>();
                    var meeting = await dbMeeting.MeetingInfos.AsNoTracking().FirstOrDefaultAsync(m => m.Id == meetingId);
                    if (meeting == null || meeting.Status == 3)
                    {
                        context.Fail("Guest session has expired because the meeting is ended or not found.");
                    }
                    return;
                }

                var userName = principal?.Identity?.Name;
                var version = principal?.FindFirst("TokenVersion")?.Value;
                var db = context.HttpContext.RequestServices.GetRequiredService<AppDbContext>();
                var account = await db.AdAccounts.AsNoTracking().FirstOrDefaultAsync(x => x.UserName == userName);
                if (account == null || !account.IsActive || version != account.TokenVersion.ToString()) context.Fail("Token has been revoked.");
            }
        };
    });

// CORS — cho phép FE Vue truy cập BE
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowFE", policy =>
        policy.WithOrigins(
            "http://localhost:4200",  // Angular dev server mặc định
            "http://localhost:5173",  // Vite dev server mặc định
            "http://localhost:3000"
        )
        .AllowAnyHeader()
        .AllowAnyMethod()
        .AllowCredentials());
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

// Vite proxies /api to the HTTP development endpoint (localhost:5228).
// Redirecting that proxied request to the self-signed HTTPS endpoint makes
// browsers follow the redirect outside the proxy and breaks authentication.
if (!app.Environment.IsDevelopment())
{
    app.UseHttpsRedirection();
}
app.UseCors("AllowFE");
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapHub<MeetingHub>("/meetinghub");

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    db.Database.Migrate();
}

await DbSeeder.SeedAsync(app.Services);

app.Run();
