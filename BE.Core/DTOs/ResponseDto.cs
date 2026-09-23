namespace BE.Core.DTOs
{
    public class ResponseDto
    {
        public bool Status { get; set; }
        public string Message { get; set; }
        public object? Data { get; set; }
    }
}
