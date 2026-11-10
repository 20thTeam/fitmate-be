package com.team20.fitmate.realtime.message;

import java.util.regex.Pattern;

/**
 * 응원 내용. 클라이언트가 CHEER로 보내고, 서버가 받은 사람에게 CHEER_RECEIVED, 보낸 사람에게 CHEER_SENT로 같은 내용을 보낸다.
 * <p>
 * 리액션 종류는 디자인·PM이 아직 정하지 않았다. 그래서 서버는 정해진 목록과 맞춰 보지 않고
 * 코드 모양(영문 대문자·숫자·밑줄 1~{@value #MAX_REACTION_LENGTH}자)만 확인해 그대로 전달한다.
 *
 * @param targetParticipantId 응원받을 참가자 식별값. 같은 운동방에 있어야 한다
 * @param reaction            리액션 코드. 예) {@code "FIRE"}, {@code "CLAP"}
 */
public record CheerPayload(Long targetParticipantId, String reaction) {

    /** 리액션 코드 최대 길이 */
    public static final int MAX_REACTION_LENGTH = 30;

    /** 리액션 코드 모양: 영문 대문자·숫자·밑줄 */
    private static final Pattern REACTION_PATTERN = Pattern.compile("^[A-Z0-9_]{1," + MAX_REACTION_LENGTH + "}$");

    /**
     * 대상과 리액션 코드가 형식에 맞는지 확인한다. getter로 직렬화되지 않도록 is·get으로 시작하지 않는 이름을 쓴다.
     */
    public boolean hasValidFormat() {
        return targetParticipantId != null && targetParticipantId > 0
                && reaction != null && REACTION_PATTERN.matcher(reaction).matches();
    }
}
