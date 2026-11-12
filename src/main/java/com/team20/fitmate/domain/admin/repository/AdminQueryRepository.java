package com.team20.fitmate.domain.admin.repository;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import com.team20.fitmate.domain.xp.entity.TeamXpLog;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 운영 화면 전용 조회. 참가자용 API와 달리 참가자 구분 없이 전체를 읽는다.
 * <p>
 * 다른 도메인 저장소에 운영용 메서드를 섞지 않으려고 따로 둔다. 목록 하나를 만드는 데 쿼리 수가 기록 수에 따라 늘지 않게 한다.
 */
@Repository
@RequiredArgsConstructor
public class AdminQueryRepository {

    private final EntityManager em;

    /**
     * 모든 운동방을 팀과 함께 식별값 순으로 읽는다.
     */
    public List<Room> findAllRoomsWithTeam() {
        return em.createQuery("select r from Room r join fetch r.team order by r.id asc", Room.class)
                .getResultList();
    }

    /**
     * 운동방에 입장한 참가자를 모두 읽는다(참가자 식별값 순). 운동방이 없는 참가자는 뺀다.
     */
    public List<Participant> findParticipantsInRooms() {
        return em.createQuery("select p from Participant p join fetch p.room order by p.id asc", Participant.class)
                .getResultList();
    }

    /**
     * 모든 참가자를 최근 생성 순으로 읽는다. 운동방은 식별값만 쓰지만 LAZY 추가 조회를 막으려고 함께 읽는다.
     */
    public List<Participant> findAllParticipantsLatestFirst() {
        return em.createQuery("select p from Participant p left join fetch p.room "
                        + "order by p.createdAt desc, p.id desc", Participant.class)
                .getResultList();
    }

    /**
     * 참가자별 종료(확정)된 운동 기록 수. 기록이 없는 참가자는 결과에 없다.
     */
    public Map<Long, Long> countFinishedWorkoutsByParticipant() {
        return em.createQuery("select w.participant.id, count(w) from Workout w "
                        + "where w.status = :status group by w.participant.id", Object[].class)
                .setParameter("status", WorkoutStatus.FINISHED)
                .getResultList().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /**
     * 운동 기록을 최근 시작 순으로 읽는다(진행 중 포함). 참가자·루틴·운동방을 함께 읽는다.
     *
     * @param participantId 이 참가자 기록만 읽는다. null이면 전체
     */
    public List<Workout> findWorkoutsLatestFirst(Long participantId) {
        return em.createQuery("select w from Workout w join fetch w.participant "
                        + "left join fetch w.routine left join fetch w.room "
                        + "where (:participantId is null or w.participant.id = :participantId) "
                        + "order by w.startedAt desc, w.id desc", Workout.class)
                .setParameter("participantId", participantId)
                .getResultList();
    }

    /**
     * 운동 기록별 측정된 유효 횟수 합. 측정된(MEASURED) 수행 기록 중 유효 횟수가 있는 것만 더한다.
     * 더할 값이 하나도 없는 운동 기록은 결과에 없다(호출하는 쪽이 0이 아니라 null로 보여준다).
     */
    public Map<Long, Integer> sumMeasuredValidCountByWorkout(Collection<Long> workoutIds) {
        if (workoutIds.isEmpty()) {
            return Map.of();
        }
        return em.createQuery("select r.workout.id, sum(r.validCount) from ExerciseRecord r "
                        + "where r.workout.id in :workoutIds and r.measurementStatus = :measured "
                        + "and r.validCount is not null group by r.workout.id", Object[].class)
                .setParameter("workoutIds", workoutIds)
                .setParameter("measured", MeasurementStatus.MEASURED)
                .getResultList().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
    }

    /**
     * 운동 기록별 팀 경험치 적립 기록. 적립 기록이 없는 운동 기록은 결과에 없다.
     */
    public Map<Long, Integer> findEarnedXpByWorkout(Collection<Long> workoutIds) {
        if (workoutIds.isEmpty()) {
            return Map.of();
        }
        return em.createQuery("select l from TeamXpLog l where l.workout.id in :workoutIds", TeamXpLog.class)
                .setParameter("workoutIds", workoutIds)
                .getResultList().stream()
                .collect(Collectors.toMap(log -> log.getWorkout().getId(), TeamXpLog::getEarnedXp));
    }
}
