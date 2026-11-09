package com.team20.fitmate.domain.workout.entity;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.routine.repository.RoutineRepository;
import com.team20.fitmate.domain.routine.service.RoutineService;
import com.team20.fitmate.domain.workout.repository.ExerciseRecordRepository;
import com.team20.fitmate.domain.workout.repository.WorkoutRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운동 기록과 수행 기록이 실제 DB에 저장·조회되는지 확인하는 테스트.
 */
@SpringBootTest
@Transactional
class ExerciseRecordPersistenceTest {

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Autowired
    private ExerciseRecordRepository exerciseRecordRepository;

    @Autowired
    private EntityManager em;

    private Workout workout;

    private List<RoutineExercise> routineExercises;

    @BeforeEach
    void setUp() {
        String token = participantService.create(new ParticipantCreateRequest("아현", "CHARACTER_01")).clientToken();
        Participant participant = participantService.findByClientToken(token);
        RoutineResponse routineResponse = routineService.createFirstRound(token, Goal.POSTURE);
        Routine routine = routineRepository.findById(routineResponse.routineId()).orElseThrow();
        routineExercises = routine.getRoutineExercises();
        workout = workoutRepository.save(Workout.start(participant, routine, null, LocalDateTime.now()));
    }

    @Test
    @DisplayName("진행 중 운동 기록에 운동별 수행 기록을 저장하고, 자세 결과 JSON을 그대로 다시 읽는다")
    void saveAndRead() {
        assertThat(workout.getStatus()).isEqualTo(WorkoutStatus.IN_PROGRESS);
        String posture = "{\"feedback\":[\"무릎이 발끝을 넘어요\"],\"belowStandardReasons\":{\"KNEE_OVER_TOE\":2}}";

        exerciseRecordRepository.save(ExerciseRecord.of(workout, routineExercises.get(0),
                new ExerciseRecord.Values(MeasurementStatus.MEASURED, 10, 2, 1, 95, 3, posture)));
        exerciseRecordRepository.save(ExerciseRecord.of(workout, routineExercises.get(1),
                new ExerciseRecord.Values(MeasurementStatus.NOT_MEASURED, null, null, null, 30, 1, null)));
        em.flush();
        em.clear();

        List<ExerciseRecord> records = exerciseRecordRepository.findByWorkoutIdOrderByIdAsc(workout.getId());
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getPostureResult()).isEqualTo(posture);
        assertThat(records.get(0).getValidCount()).isEqualTo(10);
        assertThat(records.get(1).getMeasurementStatus()).isEqualTo(MeasurementStatus.NOT_MEASURED);
        assertThat(records.get(1).getValidCount()).isNull();
    }

    @Test
    @DisplayName("같은 운동 기록에 같은 루틴 운동의 수행 기록을 두 번 저장할 수 없다")
    void duplicate() {
        ExerciseRecord.Values values = new ExerciseRecord.Values(MeasurementStatus.MEASURED, 10, 0, 0, 60, 3, null);
        exerciseRecordRepository.save(ExerciseRecord.of(workout, routineExercises.get(0), values));
        em.flush();

        assertThatThrownBy(() -> {
            exerciseRecordRepository.save(ExerciseRecord.of(workout, routineExercises.get(0), values));
            em.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class,
                org.hibernate.exception.ConstraintViolationException.class);
    }
}
