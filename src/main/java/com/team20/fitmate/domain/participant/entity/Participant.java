package com.team20.fitmate.domain.participant.entity;

import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 운동방 연결({@code room_id})은 운동방 입장 슬라이스(S2)에서 추가한다.
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
}
