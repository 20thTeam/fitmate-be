package com.team20.fitmate.domain.workout.service;

import com.team20.fitmate.domain.history.dto.HistoryResponse;
import com.team20.fitmate.domain.history.dto.HistoryWorkoutResponse;
import com.team20.fitmate.domain.history.service.HistoryService;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.service.RoutineService;
import com.team20.fitmate.domain.workout.PublicPartyTestClock;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordResponse;
import com.team20.fitmate.domain.workout.dto.PublicPartyFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResponse;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.WorkoutKind;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import com.team20.fitmate.domain.xp.entity.XpStatus;
import com.team20.fitmate.domain.xp.repository.TeamXpLogRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공개 파티(선셋 에어로빅) 운동 기록의 시작·종료와 오픈 시간, 팀 경험치·성장 기록과의 관계를 확인하는 테스트 (이슈 #11).
 * 시각은 {@link PublicPartyTestClock}으로 고정한다(기본은 열린 시각).
 */
@SpringBootTest
@Transactional
@Import(PublicPartyTestClock.class)
class PublicPartyWorkoutServiceTest {

    @Autowired
    private PublicPartyTestClock.MutableClock clock;

    @Autowired
    private PublicPartyWorkoutService publicPartyWorkoutService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private TeamXpLogRepository teamXpLogRepository;

    @BeforeEach
    void openParty() {
        clock.set(PublicPartyTestClock.OPEN);
    }

    /** 참가자를 만들고 (선택) 친구 운동방에 입장시킨 뒤 토큰을 돌려준다 */
    private String participant(String nickname, boolean enterRoom) {
        String token = participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
        if (enterRoom) {
            roomService.enter(token, roomService.getAll().getFirst().roomId());
        }
        return token;
    }

    /** 점핑잭을 측정한 기록 */
    private static PublicPartyFinishRequest measured(int validCount, int seconds) {
        return new PublicPartyFinishRequest(MeasurementStatus.MEASURED, validCount, 3, 1, seconds, null);
    }

    /** CustomException의 오류 코드를 확인한다 */
    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    @DisplayName("친구 운동방에 들어가지 않아도 토큰만으로 시작하고, 루틴·운동방 없는 공개 파티 기록이 진행 중으로 생긴다")
    void start() {
        WorkoutResponse workout = publicPartyWorkoutService.start(participant("아현", false));

        assertThat(workout.kind()).isEqualTo(WorkoutKind.PUBLIC_PARTY);
        assertThat(workout.routineId()).isNull();
        assertThat(workout.roomId()).isNull();
        assertThat(workout.status()).isEqualTo(WorkoutStatus.IN_PROGRESS);
        assertThat(workout.records()).isEmpty();
    }

    @Test
    @DisplayName("진행 중에 다시 시작하면 같은 기록을 이어 쓰고, 종료한 뒤 시작하면 새 기록이 생긴다")
    void resumeInProgress() {
        String token = participant("아현", false);
        Long first = publicPartyWorkoutService.start(token).workoutId();

        assertThat(publicPartyWorkoutService.start(token).workoutId()).isEqualTo(first);

        publicPartyWorkoutService.finish(token, first, measured(10, 60));
        assertThat(publicPartyWorkoutService.start(token).workoutId()).isNotEqualTo(first);
    }

    @Test
    @DisplayName("공개 파티가 닫혀 있으면 새로 시작할 수 없고 409-4로 거부하며 기록을 만들지 않는다")
    void rejectStartWhenClosed() {
        String token = participant("아현", false);
        clock.set(PublicPartyTestClock.CLOSED);

        assertError(() -> publicPartyWorkoutService.start(token), ErrorCode.PUBLIC_PARTY_CLOSED);
        assertThat(historyService.getMyHistory(token).workouts()).isEmpty();
    }

    @Test
    @DisplayName("열려 있을 때 시작한 운동은 닫힌 뒤에도 이어 하고 종료해 기록을 저장할 수 있다")
    void continueAndFinishAfterClose() {
        String token = participant("아현", false);
        Long workoutId = publicPartyWorkoutService.start(token).workoutId();
        clock.set(PublicPartyTestClock.CLOSED);

        assertThat(publicPartyWorkoutService.start(token).workoutId()).isEqualTo(workoutId);
        assertThat(publicPartyWorkoutService.finish(token, workoutId, measured(30, 300)).status())
                .isEqualTo(WorkoutStatus.FINISHED);
        assertError(() -> publicPartyWorkoutService.start(token), ErrorCode.PUBLIC_PARTY_CLOSED);
    }

    @Test
    @DisplayName("종료하면 보낸 값 그대로 점핑잭 기록 하나가 확정되고, 결과 조회에서 같은 값이 나온다")
    void finish() {
        String token = participant("아현", false);
        Long workoutId = publicPartyWorkoutService.start(token).workoutId();

        WorkoutResponse finished = publicPartyWorkoutService.finish(token, workoutId, measured(48, 600));

        assertThat(finished.status()).isEqualTo(WorkoutStatus.FINISHED);
        assertThat(finished.finishedAt()).isNotNull();
        assertThat(finished.records()).singleElement().satisfies(record -> {
            assertThat(record.exerciseCode()).isEqualTo("JUMPING_JACK");
            assertThat(record.routineExerciseId()).isNull();
            assertThat(record.measurementStatus()).isEqualTo(MeasurementStatus.MEASURED);
            assertThat(record.validCount()).isEqualTo(48);
            assertThat(record.belowStandardCount()).isEqualTo(3);
            assertThat(record.undeterminedCount()).isEqualTo(1);
            assertThat(record.actualSeconds()).isEqualTo(600);
            assertThat(record.performedSets()).isNull();
        });
        assertThat(workoutService.getResult(token, workoutId).workout().records())
                .extracting(ExerciseRecordResponse::validCount)
                .containsExactly(48);
    }

    @Test
    @DisplayName("측정하지 못하면 값을 0으로 채우지 않고 null로 저장한다")
    void notMeasured() {
        String token = participant("아현", false);
        Long workoutId = publicPartyWorkoutService.start(token).workoutId();

        WorkoutResponse finished = publicPartyWorkoutService.finish(token, workoutId,
                new PublicPartyFinishRequest(MeasurementStatus.NOT_MEASURED, null, null, null, null, null));

        assertThat(finished.records()).singleElement().satisfies(record -> {
            assertThat(record.measurementStatus()).isEqualTo(MeasurementStatus.NOT_MEASURED);
            assertThat(record.validCount()).isNull();
            assertThat(record.actualSeconds()).isNull();
        });
    }

    @Test
    @DisplayName("측정 못 함인데 횟수를 보내거나, 측정됨인데 횟수가 없으면 400-4로 거부하고 기록을 만들지 않는다")
    void rejectInvalidValues() {
        String token = participant("아현", false);
        Long workoutId = publicPartyWorkoutService.start(token).workoutId();

        assertError(() -> publicPartyWorkoutService.finish(token, workoutId,
                        new PublicPartyFinishRequest(MeasurementStatus.NOT_MEASURED, 10, null, null, null, null)),
                ErrorCode.INVALID_EXERCISE_RECORD);
        assertError(() -> publicPartyWorkoutService.finish(token, workoutId,
                        new PublicPartyFinishRequest(MeasurementStatus.MEASURED, null, null, null, 60, null)),
                ErrorCode.INVALID_EXERCISE_RECORD);
    }

    @Test
    @DisplayName("이미 종료한 기록을 다시 종료하면 409-1로 거부한다")
    void rejectFinishTwice() {
        String token = participant("아현", false);
        Long workoutId = publicPartyWorkoutService.start(token).workoutId();
        publicPartyWorkoutService.finish(token, workoutId, measured(10, 60));

        assertError(() -> publicPartyWorkoutService.finish(token, workoutId, measured(20, 60)),
                ErrorCode.WORKOUT_ALREADY_FINISHED);
    }

    @Test
    @DisplayName("다른 참가자의 기록이나 루틴 운동 기록은 공개 파티 종료로 확정할 수 없고 404-5로 거부한다")
    void rejectOthersAndRoutineWorkout() {
        String owner = participant("아현", false);
        Long publicWorkoutId = publicPartyWorkoutService.start(owner).workoutId();
        assertError(() -> publicPartyWorkoutService.finish(participant("동환", false), publicWorkoutId, measured(10, 60)),
                ErrorCode.WORKOUT_NOT_FOUND);

        RoutineResponse routine = routineService.createFirstRound(owner, Goal.POSTURE);
        Long routineWorkoutId = workoutService.start(owner, routine.routineId()).workoutId();
        assertError(() -> publicPartyWorkoutService.finish(owner, routineWorkoutId, measured(10, 60)),
                ErrorCode.WORKOUT_NOT_FOUND);
    }

    @Test
    @DisplayName("공개 파티 기록을 루틴 운동 종료 API로 보내면 400-4로 거부한다")
    void rejectRoutineFinishForPublicParty() {
        String token = participant("아현", false);
        Long workoutId = publicPartyWorkoutService.start(token).workoutId();

        assertError(() -> workoutService.finish(token, workoutId, new WorkoutFinishRequest(List.of())),
                ErrorCode.INVALID_EXERCISE_RECORD);
    }

    @Test
    @DisplayName("친구 운동방에 입장해 있어도 공개 파티 운동은 팀 경험치를 쌓지 않는다")
    void noTeamXp() {
        String token = participant("아현", true);
        Long workoutId = publicPartyWorkoutService.start(token).workoutId();
        publicPartyWorkoutService.finish(token, workoutId, measured(48, 600));

        assertThat(teamXpLogRepository.existsByWorkoutId(workoutId)).isFalse();
        assertThat(workoutService.getResult(token, workoutId).teamXp().status()).isEqualTo(XpStatus.NO_TEAM);
    }

    @Test
    @DisplayName("성장 기록에 공개 파티 회차가 루틴 운동과 함께 최근 순으로 나오고, 회차·목표·변경 이유·다음 추천은 비어 있다")
    void history() {
        String token = participant("아현", false);
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        Long routineWorkoutId = workoutService.start(token, routine.routineId()).workoutId();
        workoutService.finish(token, routineWorkoutId, new WorkoutFinishRequest(List.of()));
        Long publicWorkoutId = publicPartyWorkoutService.start(token).workoutId();
        publicPartyWorkoutService.finish(token, publicWorkoutId, measured(48, 600));

        HistoryResponse history = historyService.getMyHistory(token);

        assertThat(history.workouts()).extracting(HistoryWorkoutResponse::workoutId)
                .containsExactly(publicWorkoutId, routineWorkoutId);
        HistoryWorkoutResponse party = history.workouts().getFirst();
        assertThat(party.kind()).isEqualTo(WorkoutKind.PUBLIC_PARTY);
        assertThat(party.routineId()).isNull();
        assertThat(party.roundNo()).isNull();
        assertThat(party.goal()).isNull();
        assertThat(party.goalName()).isNull();
        assertThat(party.adjustmentType()).isNull();
        assertThat(party.adjustmentReasons()).isEmpty();
        assertThat(party.nextRoutine()).isNull();
        assertThat(party.totalValidCount()).isEqualTo(48);
        assertThat(party.exerciseSeconds()).isEqualTo(600);
        assertThat(history.workouts().get(1).kind()).isEqualTo(WorkoutKind.ROUTINE);
        assertThat(history.summary().workoutCount()).isEqualTo(2);
        assertThat(history.summary().totalValidCount()).isEqualTo(48);
    }
}
