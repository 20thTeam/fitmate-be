package com.team20.fitmate.domain.participant.entity;

import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 참가자. 회원가입 없이 닉네임과 캐릭터로 구별되는 임시 사용자다.
 * <p>
 * 닉네임은 중복될 수 있으므로 같은 사람인지는 {@code id}와 {@code clientToken}으로만 판단한다.
 * {@code clientToken}은 프론트가 기기에 보관했다가 재방문 때 보내는 값이다.
 * {@code room}은 마지막으로 입장한 운동방이다. 현재 접속 여부는 실시간 계층에서 관리한다.
 */
@Getter
@Entity
@Table(name = "participant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant extends BaseTimeEntity {

    /** 임시 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기기에 보관하는 재방문 식별 토큰. 서버가 무작위로 발급한다 */
    @Column(name = "client_token", nullable = false, unique = true, length = 36)
    private String clientToken;

    /** 닉네임. 중복을 허용한다 */
    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    /** 선택한 캐릭터 코드 */
    @Column(name = "character_code", nullable = false, length = 30)
    private String characterCode;

    /** 입장한 운동방. 아직 입장 전이면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    private Participant(String clientToken, String nickname, String characterCode) {
        this.clientToken = clientToken;
        this.nickname = nickname;
        this.characterCode = characterCode;
    }

    /**
     * 닉네임과 캐릭터로 새 참가자를 만든다. 재방문 토큰은 여기서 새로 발급한다.
     */
    public static Participant create(String nickname, String characterCode) {
        return new Participant(UUID.randomUUID().toString(), nickname, characterCode);
    }

    /**
     * 운동방에 입장한다. 다른 방에 있었다면 이 방으로 옮긴다.
     */
    public void enterRoom(Room room) {
        this.room = room;
    }

    /**
     * 운동방에서 나간다. 이후 마지막 입장 방이 없어져, 다음에는 방을 다시 고른다.
     */
    public void leaveRoom() {
        this.room = null;
    }
}
