package com.team20.fitmate.realtime.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CheerPayload}가 대상과 리액션 코드의 모양만 검사하는지 확인한다.
 * 리액션 종류는 미정이라 정해진 목록과 맞춰 보지 않는다.
 */
class CheerPayloadTest {

    @ParameterizedTest(name = "리액션 \"{0}\"은 받는다")
    @ValueSource(strings = {"FIRE", "CLAP", "HEART_2", "A", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123"})
    @DisplayName("영문 대문자·숫자·밑줄 1~30자 리액션 코드는 받는다")
    void acceptsReactionCode(String reaction) {
        assertThat(new CheerPayload(2L, reaction).hasValidFormat()).isTrue();
    }

    @ParameterizedTest(name = "리액션 \"{0}\"은 거절한다")
    @ValueSource(strings = {"", "fire", "불꽃", "FIRE!", "FIRE UP", "ABCDEFGHIJKLMNOPQRSTUVWXYZ01234"})
    @DisplayName("빈 값, 소문자, 한글, 기호, 공백, 31자 이상 리액션은 거절한다")
    void rejectsReactionCode(String reaction) {
        assertThat(new CheerPayload(2L, reaction).hasValidFormat()).isFalse();
    }

    @Test
    @DisplayName("대상이 없거나 0 이하이면, 리액션이 없으면 거절한다")
    void rejectsMissingValues() {
        assertThat(new CheerPayload(null, "FIRE").hasValidFormat()).isFalse();
        assertThat(new CheerPayload(0L, "FIRE").hasValidFormat()).isFalse();
        assertThat(new CheerPayload(-1L, "FIRE").hasValidFormat()).isFalse();
        assertThat(new CheerPayload(2L, null).hasValidFormat()).isFalse();
    }
}
