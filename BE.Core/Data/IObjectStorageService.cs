namespace BE.Core.Data
{
    public interface IObjectStorageService
    {
        Task UploadAsync(string objectName, Stream stream, long size, string contentType, CancellationToken cancellationToken = default);
        Task<string> GetDownloadUrlAsync(string objectName, int expirySeconds = 900);
        Task DeleteAsync(string objectName, CancellationToken cancellationToken = default);
        string BucketName { get; }
    }
}
