package com.team20.fitmate.domain.cheer.entity;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.global.common.BaseTimeEntity;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 응원 기록. 같은 운동방 참가자끼리 전달에 성공한 응원 하나다(기능 6.1).
 * <p>
 * 실시간 계층이 대상에게 전달한 뒤 저장하므로, 저장 실패가 전달을 되돌리지는 않는다.
 * 보낸 사람과 받은 사람이 모두 이 운동방에 있어야 하고, 자기 자신에게는 보낼 수 없다.
 */
@Getter
@Entity
@Table(name = "cheer", indexes = {
        @Index(name = "idx_cheer_receiver", columnList = "receiver_id"),
        @Index(name = "idx_cheer_room", columnList = "room_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cheer extends BaseTimeEntity {

    /** 리액션 코드 최대 길이. 실시간 메시지 형식(영문 대문자·숫자·밑줄 1~30자)과 같다 */
    public static final int REACTION_MAX_LENGTH = 30;

    /** 응원 기록 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 응원이 오간 운동방 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** 보낸 참가자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Participant sender;

    /** 받은 참가자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Participant receiver;

    /** 리액션 코드 (예: FIRE). 종류는 디자인·PM 미정이라 형식만 맞으면 받는다 */
    @Column(name = "reaction", nullable = false, length = REACTION_MAX_LENGTH)
    private String reaction;

    /** 서버가 응원을 전달한 시각(UTC). 저장 시각({@code created_at})과 다를 수 있다 */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private Cheer(Room room, Participant sender, Participant receiver, String reaction, Instant occurredAt) {
        this.room = room;
        this.sender = sender;
        this.receiver = receiver;
        this.reaction = reaction;
        this.occurredAt = occurredAt;
    }

    /**
     * 같은 운동방 참가자끼리 주고받은 응원을 만든다.
     *
     * @throws CustomException 보낸 사람이나 받은 사람이 이 운동방에 없거나, 자기 자신에게 보냈거나,
     *                         리액션·시각이 비어 있으면 {@link ErrorCode#INVALID_CHEER}
     */
    public static Cheer of(Room room, Participant sender, Participant receiver, String reaction, Instant occurredAt) {
        if (!isIn(sender, room) || !isIn(receiver, room)
                || Objects.equals(sender.getId(), receiver.getId())
                || reaction == null || reaction.isBlank() || reaction.length() > REACTION_MAX_LENGTH
                || occurredAt == null) {
            throw new CustomException(ErrorCode.INVALID_CHEER);
        }
        return new Cheer(room, sender, receiver, reaction, occurredAt);
    }

    /** 참가자가 마지막으로 입장한 운동방이 이 방인지 본다 */
    private static boolean isIn(Participant participant, Room room) {
        return participant.getRoom() != null && Objects.equals(participant.getRoom().getId(), room.getId());
    }
}
