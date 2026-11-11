package com.team20.fitmate.domain.history.dto;

import com.team20.fitmate.domain.routine.entity.AdjustmentType;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordResponse;
import com.team20.fitmate.domain.workout.entity.WorkoutKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 성장 기록의 회차 하나. 종료된 운동 기록 하나와 그 수행 기록, 이 회차가 바뀐 이유, 다음 추천을 담는다.
 *
 * @param workoutId         운동 기록 식별값
 * @param kind              운동 종류 (루틴 운동, 공개 파티)
 * @param routineId         이 회차 루틴 식별값. 공개 파티면 null
 * @param roundNo           회차. 공개 파티면 null
 * @param goal              목표. 공개 파티면 null
 * @param goalName          목표 한글 이름. 공개 파티면 null
 * @param startedAt         시작 시각
 * @param finishedAt        종료 시각
 * @param elapsedSeconds    시작부터 종료까지 걸린 시간(초). 쉬는 시간이 포함된다
 * @param exerciseSeconds   운동별 실제 소요 시간의 합(초). 보낸 값이 없으면 null
 * @param totalValidCount   측정된 운동의 유효 횟수 합. 측정된 횟수형 운동이 없으면 null
 * @param adjustmentType    이 회차가 이전 회차에서 바뀐 유형. 첫 회차·공개 파티는 null
 * @param adjustmentReasons 이 회차가 이전 회차에서 바뀐 이유. 첫 회차·공개 파티는 빈 목록
 * @param earnedXp          이 운동으로 팀에 적립된 경험치. 적립되지 않았으면(팀 없음 등) null
 * @param records           운동별 수행 기록 (루틴 순서)
 * @param nextRoutine       이 기록으로 만든 다음 회차 추천. 아직 추천받지 않았으면 null
 */
public record HistoryWorkoutResponse(

        @Schema(description = "운동 기록 식별값", example = "3")
        Long workoutId,

        @Schema(description = "운동 종류 (ROUTINE 루틴 운동, PUBLIC_PARTY 공개 파티)", example = "ROUTINE")
        WorkoutKind kind,

        @Schema(description = "이 회차 루틴 식별값. 공개 파티면 null", example = "1", nullable = true)
        Long routineId,

        @Schema(description = "회차. 공개 파티면 null", example = "1", nullable = true)
        Integer roundNo,

        @Schema(description = "목표. 공개 파티면 null", example = "POSTURE", nullable = true)
        Goal goal,

        @Schema(description = "목표 한글 이름. 공개 파티면 null", example = "자세 개선", nullable = true)
        String goalName,

        @Schema(description = "시작 시각")
        LocalDateTime startedAt,

        @Schema(description = "종료 시각")
        LocalDateTime finishedAt,

        @Schema(description = "시작부터 종료까지 걸린 시간(초). 쉬는 시간이 포함된다", example = "540")
        long elapsedSeconds,

        @Schema(description = "운동별 실제 소요 시간의 합(초). 보낸 값이 없으면 null", example = "420")
        Integer exerciseSeconds,

        @Schema(description = "측정된 운동의 유효 횟수 합. 측정된 횟수형 운동이 없으면 null(0이 아님)", example = "66")
        Integer totalValidCount,

        @Schema(description = "이 회차가 이전 회차에서 바뀐 유형. 첫 회차·공개 파티는 null", example = "UP")
        AdjustmentType adjustmentType,

        @Schema(description = "이 회차가 이전 회차에서 바뀐 이유. 첫 회차·공개 파티는 빈 목록")
        List<String> adjustmentReasons,

        @Schema(description = "이 운동으로 팀에 적립된 경험치. 적립되지 않았으면(팀 없음 등) null", example = "81")
        Integer earnedXp,

        @Schema(description = "운동별 수행 기록 (루틴 순서)")
        List<ExerciseRecordResponse> records,

        @Schema(description = "이 기록으로 만든 다음 회차 추천. 아직 추천받지 않았으면 null")
        NextRoutineSummary nextRoutine
) {
}
