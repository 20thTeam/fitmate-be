package com.team20.fitmate.global.config;

import com.team20.fitmate.realtime.handler.RoomWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 운동방 실시간 연결(WebSocket) 설정.
 * <p>
 * 주소는 {@code /ws} 하나다. STOMP 없이 브라우저 기본 {@code WebSocket}으로 JSON 메시지를 주고받는다.
 * 운영에서는 Caddy가 {@code wss://}를 그대로 앱으로 넘긴다({@code deploy/Caddyfile}).
 * <p>
 * 허용할 출처(Origin)는 REST CORS와 같은 {@code cors.allowed-origins}를 쓴다. 목록에 없는 출처의 연결은 403으로 거절된다.
 * 메시지 형식은 {@code docs/실시간_메시지_형식.md}에 있다. 시간 설정·스케줄러는 {@link RealtimeConfig}에 있다.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 실시간 연결 주소 */
    public static final String ENDPOINT = "/ws";

    private final RoomWebSocketHandler roomWebSocketHandler;

    /** 연결을 허용할 프론트 주소 목록 */
    private final String[] allowedOrigins;

    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler,
                           @Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 운동방 핸들러를 {@code /ws}에 등록하고 허용 출처를 지정한다.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, ENDPOINT)
                .setAllowedOrigins(allowedOrigins);
    }
}
