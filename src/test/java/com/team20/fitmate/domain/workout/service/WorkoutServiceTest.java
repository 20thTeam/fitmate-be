package com.team20.fitmate.domain.workout.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.domain.routine.dto.RoutineExerciseResponse;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.repository.RoutineRepository;
import com.team20.fitmate.domain.routine.service.RoutineService;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordRequest;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordResponse;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResponse;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import com.team20.fitmate.domain.workout.repository.ExerciseRecordRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WorkoutService}의 운동 시작·종료와 재제출 거부를 확인하는 테스트.
 * 루틴은 자세 개선 목표(스쿼트 횟수형 + 플랭크 시간형)로 만든다.
 */
@SpringBootTest
@Transactional
class WorkoutServiceTest {

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ExerciseRecordRepository exerciseRecordRepository;

    private String token;

    private RoutineResponse routine;

    @BeforeEach
    void setUp() {
        token = createParticipant("아현");
        routine = routineService.createFirstRound(token, Goal.POSTURE);
    }

    /** 테스트용 참가자를 만들고 재방문 토큰을 돌려준다 */
    private String createParticipant(String nickname) {
        return participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
    }

    /** 루틴의 n번째(0부터) 운동 식별값 */
    private Long routineExerciseId(RoutineResponse routineResponse, int index) {
        List<RoutineExerciseResponse> exercises = routineResponse.exercises();
        return exercises.get(index).routineExerciseId();
    }

    /** 스쿼트(횟수형)를 측정한 수행 기록 */
    private ExerciseRecordRequest squatMeasured(Long routineExerciseId) {
        return new ExerciseRecordRequest(routineExerciseId, MeasurementStatus.MEASURED, 10, 2, 1, 60, 3, null);
    }

    /** CustomException의 오류 코드를 확인한다 */
    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    @DisplayName("운동을 시작하면 운동 기록 식별값이 발급되고 진행 중이며, 입장한 운동방이 기록된다")
    void start() {
        Long roomId = roomService.getAll().getFirst().roomId();
        roomService.enter(token, roomId);

        WorkoutResponse workout = workoutService.start(token, routine.routineId());

        assertThat(workout.workoutId()).isNotNull();
        assertThat(workout.status()).isEqualTo(WorkoutStatus.IN_PROGRESS);
        assertThat(workout.routineId()).isEqualTo(routine.routineId());
        assertThat(workout.roomId()).isEqualTo(roomId);
        assertThat(workout.finishedAt()).isNull();
        assertThat(workout.records()).isEmpty();
    }

    @Test
    @DisplayName("같은 루틴으로 진행 중이면 다시 시작해도 같은 운동 기록을 이어 쓴다(재접속)")
    void startAgainWhileInProgress() {
        WorkoutResponse first = workoutService.start(token, routine.routineId());

        WorkoutResponse again = workoutService.start(token, routine.routineId());

        assertThat(again.workoutId()).isEqualTo(first.workoutId());
    }

    @Test
    @DisplayName("종료한 뒤 같은 루틴으로 다시 시작하면 새 운동 기록이 발급된다")
    void startAfterFinished() {
        WorkoutResponse first = workoutService.start(token, routine.routineId());
        workoutService.finish(token, first.workoutId(), new WorkoutFinishRequest(List.of()));

        WorkoutResponse second = workoutService.start(token, routine.routineId());

        assertThat(second.workoutId()).isNotEqualTo(first.workoutId());
    }

    @Test
    @DisplayName("다른 참가자의 루틴으로는 시작할 수 없다")
    void startWithOthersRoutine() {
        String friend = createParticipant("친구");

        assertError(() -> workoutService.start(friend, routine.routineId()), ErrorCode.ROUTINE_NOT_FOUND);
    }

    @Test
    @DisplayName("추천 불가 루틴으로는 시작할 수 없다")
    void startWithUnavailableRoutine() {
        Routine unavailable = routineRepository.save(
                Routine.unavailable(participantService.findByClientToken(token), 1, Goal.POSTURE));

        assertError(() -> workoutService.start(token, unavailable.getId()), ErrorCode.ROUTINE_NOT_STARTABLE);
    }

    @Test
    @DisplayName("종료하면 루틴 순서대로 수행 기록이 확정되고, 보내지 않은 운동은 측정 못 함(null)으로 저장된다")
    void finish() {
        WorkoutResponse workout = workoutService.start(token, routine.routineId());

        WorkoutResponse finished = workoutService.finish(token, workout.workoutId(),
                new WorkoutFinishRequest(List.of(squatMeasured(routineExerciseId(routine, 0)))));

        assertThat(finished.status()).isEqualTo(WorkoutStatus.FINISHED);
        assertThat(finished.finishedAt()).isNotNull();
        assertThat(finished.records()).hasSize(2);

        ExerciseRecordResponse squat = finished.records().get(0);
        assertThat(squat.exerciseCode()).isEqualTo("SQUAT");
        assertThat(squat.measurementStatus()).isEqualTo(MeasurementStatus.MEASURED);
        assertThat(squat.validCount()).isEqualTo(10);

        ExerciseRecordResponse plank = finished.records().get(1);
        assertThat(plank.exerciseCode()).isEqualTo("PLANK");
        assertThat(plank.measurementStatus()).isEqualTo(MeasurementStatus.NOT_MEASURED);
        assertThat(plank.validCount()).isNull();
        assertThat(plank.actualSeconds()).isNull();
    }

    @Test
    @DisplayName("이미 종료된 운동 기록에 다시 제출하면 거부하고 기록이 늘지 않는다")
    void finishTwice() {
        WorkoutResponse workout = workoutService.start(token, routine.routineId());
        WorkoutFinishRequest request = new WorkoutFinishRequest(List.of(squatMeasured(routineExerciseId(routine, 0))));
        workoutService.finish(token, workout.workoutId(), request);

        assertError(() -> workoutService.finish(token, workout.workoutId(), request),
                ErrorCode.WORKOUT_ALREADY_FINISHED);
        assertThat(exerciseRecordRepository.findByWorkoutIdOrderByIdAsc(workout.workoutId())).hasSize(2);
    }

    @Test
    @DisplayName("다른 참가자의 운동 기록은 종료할 수 없다")
    void finishOthersWorkout() {
        WorkoutResponse workout = workoutService.start(token, routine.routineId());
        String friend = createParticipant("친구");

        assertError(() -> workoutService.finish(friend, workout.workoutId(), new WorkoutFinishRequest(List.of())),
                ErrorCode.WORKOUT_NOT_FOUND);
    }

    @Test
    @DisplayName("루틴에 없는 운동이나 같은 운동을 두 번 보내면 거부한다")
    void finishWithInvalidExercises() {
        WorkoutResponse workout = workoutService.start(token, routine.routineId());
        Long squatId = routineExerciseId(routine, 0);
        RoutineResponse otherRoutine = routineService.createFirstRound(token, Goal.STRENGTH);

        assertError(() -> workoutService.finish(token, workout.workoutId(), new WorkoutFinishRequest(
                        List.of(squatMeasured(routineExerciseId(otherRoutine, 0))))),
                ErrorCode.INVALID_EXERCISE_RECORD);
        assertError(() -> workoutService.finish(token, workout.workoutId(), new WorkoutFinishRequest(
                        List.of(squatMeasured(squatId), squatMeasured(squatId)))),
                ErrorCode.INVALID_EXERCISE_RECORD);
    }

    @Test
    @DisplayName("측정 못 함인데 횟수를 보내면 근거 없는 값이라 거부한다")
    void finishWithUngroundedCount() {
        WorkoutResponse workout = workoutService.start(token, routine.routineId());
        ExerciseRecordRequest ungrounded = new ExerciseRecordRequest(
                routineExerciseId(routine, 0), MeasurementStatus.NOT_MEASURED, 5, null, null, null, null, null);

        assertError(() -> workoutService.finish(token, workout.workoutId(),
                new WorkoutFinishRequest(List.of(ungrounded))), ErrorCode.INVALID_EXERCISE_RECORD);
    }

    @Test
    @DisplayName("친구가 먼저 종료해도 내 운동 기록은 진행 중 그대로다")
    void friendFinishDoesNotAffectMe() {
        String friend = createParticipant("친구");
        RoutineResponse friendRoutine = routineService.createFirstRound(friend, Goal.POSTURE);
        WorkoutResponse mine = workoutService.start(token, routine.routineId());
        WorkoutResponse friends = workoutService.start(friend, friendRoutine.routineId());

        workoutService.finish(friend, friends.workoutId(), new WorkoutFinishRequest(List.of()));

        assertThat(workoutService.start(token, routine.routineId()).workoutId()).isEqualTo(mine.workoutId());
        assertThat(workoutService.start(token, routine.routineId()).status()).isEqualTo(WorkoutStatus.IN_PROGRESS);
    }
}
