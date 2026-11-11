package com.team20.fitmate.domain.admin.dto;

import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.entity.WorkoutKind;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 운영 화면의 운동 기록 한 건. 성장 기록({@code HistoryWorkoutResponse})의 요약 필드에 참가자·운동방·상태를 더했다.
 * <p>
 * 진행 중 기록은 확정 전이라 종료 시각·걸린 시간·유효 횟수·경험치가 모두 null이다.
 * 측정하지 못한 기록의 유효 횟수는 0이 아니라 null이다.
 *
 * @param workoutId       운동 기록 식별값
 * @param participantId   참가자 식별값
 * @param nickname        닉네임
 * @param roomId          운동한 운동방 식별값. 공개 파티면 null
 * @param kind            운동 종류 (루틴 운동, 공개 파티)
 * @param routineId       루틴 식별값. 공개 파티면 null
 * @param roundNo         회차. 공개 파티면 null
 * @param goal            목표. 공개 파티면 null
 * @param goalName        목표 한글 이름. 공개 파티면 null
 * @param status          상태 (진행 중, 종료)
 * @param startedAt       시작 시각
 * @param finishedAt      종료 시각. 진행 중이면 null
 * @param elapsedSeconds  시작부터 종료까지 걸린 시간(초). 진행 중이면 null
 * @param totalValidCount 측정된 운동의 유효 횟수 합. 진행 중이거나 측정된 횟수가 없으면 null
 * @param earnedXp        이 운동으로 팀에 적립된 경험치. 진행 중이거나 적립되지 않았으면 null
 */
public record AdminWorkoutResponse(

        @Schema(description = "운동 기록 식별값", example = "31")
        Long workoutId,

        @Schema(description = "참가자 식별값", example = "12")
        Long participantId,

        @Schema(description = "닉네임", example = "민지")
        String nickname,

        @Schema(description = "운동한 운동방 식별값. 공개 파티면 null", example = "1", nullable = true)
        Long roomId,

        @Schema(description = "운동 종류 (ROUTINE 루틴 운동, PUBLIC_PARTY 공개 파티)", example = "ROUTINE")
        WorkoutKind kind,

        @Schema(description = "루틴 식별값. 공개 파티면 null", example = "20", nullable = true)
        Long routineId,

        @Schema(description = "회차. 공개 파티면 null", example = "2", nullable = true)
        Integer roundNo,

        @Schema(description = "목표. 공개 파티면 null", example = "POSTURE", nullable = true)
        Goal goal,

        @Schema(description = "목표 한글 이름. 공개 파티면 null", example = "자세 개선", nullable = true)
        String goalName,

        @Schema(description = "상태 (IN_PROGRESS 진행 중, FINISHED 종료)", example = "FINISHED")
        WorkoutStatus status,

        @Schema(description = "시작 시각", example = "2026-11-11T20:20:00")
        LocalDateTime startedAt,

        @Schema(description = "종료 시각. 진행 중이면 null", example = "2026-11-11T20:31:00", nullable = true)
        LocalDateTime finishedAt,

        @Schema(description = "시작부터 종료까지 걸린 시간(초). 진행 중이면 null", example = "660", nullable = true)
        Long elapsedSeconds,

        @Schema(description = "측정된 운동의 유효 횟수 합. 진행 중이거나 측정된 횟수가 없으면 null(0이 아님)",
                example = "63", nullable = true)
        Integer totalValidCount,

        @Schema(description = "팀에 적립된 경험치. 진행 중이거나 적립되지 않았으면 null", example = "63", nullable = true)
        Integer earnedXp
) {

    /**
     * 운동 기록과 미리 모아 둔 유효 횟수 합·경험치로 응답을 만든다.
     * 참가자·루틴·운동방(LAZY)을 읽으므로 트랜잭션 안에서 부른다. 진행 중이면 확정값은 넘겨받아도 null로 둔다.
     */
    public static AdminWorkoutResponse of(Workout workout, Integer totalValidCount, Integer earnedXp) {
        Routine routine = workout.getRoutine();
        boolean finished = workout.isFinished();
        return new AdminWorkoutResponse(
                workout.getId(),
                workout.getParticipant().getId(),
                workout.getParticipant().getNickname(),
                workout.getRoom() == null ? null : workout.getRoom().getId(),
                workout.getKind(),
                routine == null ? null : routine.getId(),
                routine == null ? null : routine.getRoundNo(),
                routine == null ? null : routine.getGoal(),
                routine == null ? null : routine.getGoal().getDisplayName(),
                workout.getStatus(),
                workout.getStartedAt(),
                finished ? workout.getFinishedAt() : null,
                finished ? Duration.between(workout.getStartedAt(), workout.getFinishedAt()).toSeconds() : null,
                finished ? totalValidCount : null,
                finished ? earnedXp : null);
    }
}
