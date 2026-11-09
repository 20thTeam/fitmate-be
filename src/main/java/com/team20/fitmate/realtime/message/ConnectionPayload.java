package com.team20.fitmate.realtime.message;

import com.team20.fitmate.realtime.room.ConnectionStatus;

/**
 * CONNECTION_STATUS 내용.
 *
 * @param connectionStatus 바뀐 연결 상태
 */
public record ConnectionPayload(ConnectionStatus connectionStatus) {
}
