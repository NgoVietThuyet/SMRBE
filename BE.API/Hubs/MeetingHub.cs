using Microsoft.AspNetCore.SignalR;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;

namespace BE.API.Hubs
{
    public class MeetingHub : Hub
    {
        private readonly ILogger<MeetingHub> _logger;

        public MeetingHub(ILogger<MeetingHub> logger)
        {
            _logger = logger;
        }

        public async Task JoinMeeting(string meetingId)
        {
            await Groups.AddToGroupAsync(Context.ConnectionId, meetingId);
            _logger.LogInformation($"Client {Context.ConnectionId} joined meeting group {meetingId}");
            await Clients.Group(meetingId).SendAsync("ParticipantJoined", Context.ConnectionId);
        }

        public async Task LeaveMeeting(string meetingId)
        {
            await Groups.RemoveFromGroupAsync(Context.ConnectionId, meetingId);
            _logger.LogInformation($"Client {Context.ConnectionId} left meeting group {meetingId}");
            await Clients.Group(meetingId).SendAsync("ParticipantLeft", Context.ConnectionId);
        }

        public async Task SendMessage(string meetingId, object messageEnvelope)
        {
            await Clients.Group(meetingId).SendAsync("ReceiveMessage", messageEnvelope);
        }
    }
}
