package com.team20.fitmate.realtime.room;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 레지스트리 단위 테스트용 가짜 연결. 보낸 메시지를 모아 두고, 닫힘 상태를 기록한다.
 */
class FakeWebSocketSession implements WebSocketSession {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String id;
    private final List<JsonNode> sent = new ArrayList<>();
    private CloseStatus closeStatus;

    FakeWebSocketSession(String id) {
        this.id = id;
    }

    /** 지금까지 받은 메시지 */
    List<JsonNode> sent() {
        return sent;
    }

    /** 받은 메시지 중 해당 종류만 */
    List<JsonNode> sentOf(String type) {
        return sent.stream().filter(node -> type.equals(node.path("type").asString())).toList();
    }

    /** 마지막으로 받은 메시지 */
    JsonNode last() {
        return sent.getLast();
    }

    /** 서버가 닫았을 때의 상태. 열려 있으면 null */
    CloseStatus closeStatus() {
        return closeStatus;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) {
        sent.add(MAPPER.readTree(((TextMessage) message).getPayload()));
    }

    @Override
    public boolean isOpen() {
        return closeStatus == null;
    }

    @Override
    public void close() {
        close(CloseStatus.NORMAL);
    }

    @Override
    public void close(CloseStatus status) {
        this.closeStatus = status;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getUri() {
        return URI.create("ws://localhost/ws");
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return new HashMap<>();
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getTextMessageSizeLimit() {
        return 8192;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return 8192;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }
}
