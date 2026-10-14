package vn.com.d2s.smr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import vn.com.d2s.smr.service.mt.impl.SignalRMeetingEventPublisher;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalRMeetingEventPublisher signalRPublisher;
    private final HubAuthHandshakeInterceptor authInterceptor;

    public WebSocketConfig(SignalRMeetingEventPublisher signalRPublisher, HubAuthHandshakeInterceptor authInterceptor) {
        this.signalRPublisher = signalRPublisher;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalRPublisher, "/meetinghub")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
