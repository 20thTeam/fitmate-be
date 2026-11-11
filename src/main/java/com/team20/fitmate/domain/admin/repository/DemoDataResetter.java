package com.team20.fitmate.domain.admin.repository;

import com.team20.fitmate.domain.admin.dto.AdminResetResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리허설 때 쌓인 시연 데이터를 한 트랜잭션에서 지운다. 중간에 실패하면 아무것도 지워지지 않는다.
 * <p>
 * 지우는 것: 참가자, 루틴·루틴 운동, 운동 기록·수행 기록, 응원, 팀 경험치 적립 기록. 팀 누적 경험치는 0으로 되돌린다.
 * 남기는 것: 운동방, 팀, 운동 목록(초기 데이터).
 * <p>
 * 외래 키 순서대로 자식부터 지운다. 루틴(다음 회차 추천 근거)과 운동 기록은 서로를 가리키므로
 * 루틴의 근거 운동 기록을 먼저 비운 뒤 운동 기록을 지운다.
 */
@Repository
@RequiredArgsConstructor
public class DemoDataResetter {

    private final EntityManager em;

    /**
     * 시연 데이터를 지우고 지운 개수를 돌려준다.
     */
    @Transactional
    public AdminResetResponse reset() {
        // 1) 운동 기록·참가자를 가리키는 자식 테이블
        em.createQuery("delete from TeamXpLog").executeUpdate();
        em.createQuery("delete from ExerciseRecord").executeUpdate();
        em.createQuery("delete from Cheer").executeUpdate();

        // 2) 루틴 ↔ 운동 기록 상호 참조를 끊고 운동 기록을 지운다
        em.createQuery("update Routine r set r.basedOnWorkout = null").executeUpdate();
        int deletedWorkouts = em.createQuery("delete from Workout").executeUpdate();

        // 3) 루틴과 참가자
        em.createQuery("delete from RoutineExercise").executeUpdate();
        em.createQuery("delete from Routine").executeUpdate();
        int deletedParticipants = em.createQuery("delete from Participant").executeUpdate();

        // 4) 팀은 남기고 누적 경험치만 0으로
        int resetTeams = em.createQuery("update Team t set t.totalXp = 0").executeUpdate();

        // 일괄 쿼리는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션에서 지워진 엔티티를 읽지 않게 비운다
        em.clear();
        return new AdminResetResponse(deletedParticipants, deletedWorkouts, resetTeams);
    }
}
