# Build Stage
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Copy csproj and restore as distinct layers
COPY ["BE.csproj", "./"]
RUN dotnet restore "BE.csproj"

# Copy everything else and build an app
COPY . .
RUN dotnet build "BE.csproj" -c Release -o /app/build

# Publish the application
FROM build AS publish
RUN dotnet publish "BE.csproj" -c Release -o /app/publish /p:UseAppHost=false

# Final Stage
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app

# Khai báo port để Render bind traffic vào
# .NET 8 mặc định chạy cổng 8080 trong Docker
EXPOSE 8080
ENV ASPNETCORE_URLS=http://+:8080

COPY --from=publish /app/publish .

# Run the app
ENTRYPOINT ["dotnet", "BE.dll"]
