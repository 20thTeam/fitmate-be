package com.team20.fitmate.domain.participant.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateResponse;
import com.team20.fitmate.domain.participant.dto.ParticipantResponse;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ParticipantService}의 참가자 발급·조회를 확인하는 테스트.
 */
@SpringBootTest
@Transactional
class ParticipantServiceTest {

    @Autowired
    private ParticipantService participantService;

    @Test
    @DisplayName("닉네임과 캐릭터로 참가자를 만들면 식별값과 재방문 토큰이 발급된다")
    void create() {
        ParticipantCreateResponse response =
                participantService.create(new ParticipantCreateRequest("아현", "CHARACTER_01"));

        assertThat(response.participantId()).isNotNull();
        assertThat(response.clientToken()).isNotBlank();
        assertThat(response.nickname()).isEqualTo("아현");
        assertThat(response.characterCode()).isEqualTo("CHARACTER_01");
    }

    @Test
    @DisplayName("같은 닉네임이어도 서로 다른 참가자와 토큰으로 발급된다")
    void createSameNickname() {
        ParticipantCreateResponse first =
                participantService.create(new ParticipantCreateRequest("아현", "CHARACTER_01"));
        ParticipantCreateResponse second =
                participantService.create(new ParticipantCreateRequest("아현", "CHARACTER_01"));

        assertThat(first.participantId()).isNotEqualTo(second.participantId());
        assertThat(first.clientToken()).isNotEqualTo(second.clientToken());
    }

    @Test
    @DisplayName("닉네임 앞뒤 공백은 지우고 저장한다")
    void createStripsNickname() {
        ParticipantCreateResponse response =
                participantService.create(new ParticipantCreateRequest("  아현  ", "CHARACTER_01"));

        assertThat(response.nickname()).isEqualTo("아현");
    }

    @Test
    @DisplayName("재방문 토큰으로 본인 참가자 정보를 조회한다")
    void getByClientToken() {
        ParticipantCreateResponse created =
                participantService.create(new ParticipantCreateRequest("아현", "CHARACTER_02"));

        ParticipantResponse response = participantService.getByClientToken(created.clientToken());

        assertThat(response.participantId()).isEqualTo(created.participantId());
        assertThat(response.nickname()).isEqualTo("아현");
        assertThat(response.characterCode()).isEqualTo("CHARACTER_02");
    }

    @Test
    @DisplayName("없는 토큰으로 조회하면 PARTICIPANT_NOT_FOUND 예외가 난다")
    void getByUnknownToken() {
        assertThatThrownBy(() -> participantService.getByClientToken("없는-토큰"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARTICIPANT_NOT_FOUND);
    }
}
