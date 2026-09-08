using BE.Core.Data;
using Minio;
using Minio.DataModel.Args;

namespace BE.Service.Services
{
    public class MinioObjectStorageService : IObjectStorageService
    {
        private readonly IMinioClient _client;
        public string BucketName { get; }
        public MinioObjectStorageService(IConfiguration config)
        {
            var endpoint = config["Minio:Endpoint"] ?? throw new InvalidOperationException("Minio endpoint is missing.");
            var builder = new MinioClient().WithEndpoint(endpoint, config.GetValue("Minio:Port", 443))
                .WithCredentials(config["Minio:AccessKey"], config["Minio:SecretKey"]);
            if (config.GetValue("Minio:UseSSL", true)) builder = builder.WithSSL();
            _client = builder.Build();
            BucketName = config["Minio:BucketName"] ?? throw new InvalidOperationException("Minio bucket is missing.");
        }
        private async Task EnsureBucket(CancellationToken ct)
        {
            if (!await _client.BucketExistsAsync(new BucketExistsArgs().WithBucket(BucketName), ct))
                await _client.MakeBucketAsync(new MakeBucketArgs().WithBucket(BucketName), ct);
        }
        public async Task UploadAsync(string objectName, Stream stream, long size, string contentType, CancellationToken ct = default)
        {
            await EnsureBucket(ct);
            await _client.PutObjectAsync(new PutObjectArgs().WithBucket(BucketName).WithObject(objectName).WithStreamData(stream).WithObjectSize(size).WithContentType(contentType), ct);
        }
        public Task<string> GetDownloadUrlAsync(string objectName, int expirySeconds = 900) =>
            _client.PresignedGetObjectAsync(new PresignedGetObjectArgs().WithBucket(BucketName).WithObject(objectName).WithExpiry(expirySeconds));
        public Task DeleteAsync(string objectName, CancellationToken ct = default) =>
            _client.RemoveObjectAsync(new RemoveObjectArgs().WithBucket(BucketName).WithObject(objectName), ct);
    }
}
