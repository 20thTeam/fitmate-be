package com.team20.fitmate.domain.cheer.service;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;

/**
 * 실시간 계층이 <b>전달에 성공한</b> 응원을 저장하도록 넘기는 입구(이슈 #6).
 * <p>
 * 구현체는 데이터 담당(B)이 {@code Cheer} 엔티티와 함께 만든다. 구현체를 {@code @Service}로 등록하면
 * 실시간 계층이 자동으로 찾아 쓴다. 구현체가 없는 동안에는 실시간 계층이 저장 없이 로그만 남긴다.
 * <p>
 * 구현 시 지킬 것:
 * <ul>
 *     <li>보낸 사람과 받은 사람이 같은 운동방 참가자인지 확인한다(다른 방이면 거부). 실시간 계층도 같은 방만 넘기지만 저장 쪽에서 한 번 더 막는다.</li>
 *     <li>예외를 던져도 이미 전달된 응원은 취소되지 않는다. 실시간 계층은 예외를 로그로만 남긴다.</li>
 *     <li>메시지 처리 스레드에서 바로 불리므로 오래 걸리는 작업을 하지 않는다.</li>
 * </ul>
 */
public interface CheerRecorder {

    /**
     * 전달된 응원 하나를 저장한다.
     *
     * @param command 운동방, 보낸 사람, 받은 사람, 리액션, 이벤트 시각
     */
    void record(CheerRecordCommand command);
}
