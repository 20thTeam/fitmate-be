package com.team20.fitmate.domain.xp.service;

import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.room.repository.TeamRepository;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.xp.calculator.XpCalculator;
import com.team20.fitmate.domain.xp.dto.TeamXpChangeResponse;
import com.team20.fitmate.domain.xp.dto.TeamXpResponse;
import com.team20.fitmate.domain.xp.entity.TeamXpLog;
import com.team20.fitmate.domain.xp.entity.XpStatus;
import com.team20.fitmate.domain.xp.repository.TeamXpLogRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 팀 경험치 적립과 조회를 맡는 서비스. 경험치는 확정된 수행 기록만 입력으로 받는다.
 */
@Service
@RequiredArgsConstructor
public class TeamXpService {

    /** 참가자 1명이 하루에 적립할 수 있는 최대 경험치 (PM 확정) */
    static final int DAILY_XP_LIMIT_PER_PARTICIPANT = 100;

    /** 하루를 나누는 기준 시간대 */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final XpCalculator xpCalculator;
    private final TeamXpLogRepository teamXpLogRepository;
    private final TeamRepository teamRepository;

    /**
     * 종료된 운동 기록으로 팀 경험치를 적립한다. 운동 종료와 같은 트랜잭션에서 부른다.
     * <p>
     * 운동방(팀) 없이 운동했거나, 산식이 정해지지 않았거나, 이미 적립한 운동 기록이면 적립하지 않는다.
     * 중복은 이 검사와 {@code workout_id} 유니크 제약으로 두 번 막는다.
     * 참가자의 오늘(한국 시간) 적립 합계가 하루 최대치를 넘지 않도록 잘라서 적립한다.
     */
    @Transactional
    public void accrue(Workout workout, List<ExerciseRecord> records) {
        Team team = teamOf(workout);
        if (team == null || !xpCalculator.isDecided() || teamXpLogRepository.existsByWorkoutId(workout.getId())) {
            return;
        }

        int earnedXp = Math.min(xpCalculator.calculate(records), remainingDailyXp(workout.getParticipant().getId()));
        TeamXpLog log = teamXpLogRepository.save(TeamXpLog.of(team, workout, earnedXp));
        teamRepository.addXp(team.getId(), earnedXp);
        log.recordTeamTotal(teamRepository.findTotalXp(team.getId()));
    }

    /** 참가자가 오늘 더 적립할 수 있는 경험치 */
    private int remainingDailyXp(Long participantId) {
        LocalDateTime todayStart = LocalDate.now(SERVICE_ZONE).atStartOfDay();
        int earnedToday = teamXpLogRepository.sumEarnedXpByParticipantBetween(
                participantId, todayStart, todayStart.plusDays(1));
        return Math.max(0, DAILY_XP_LIMIT_PER_PARTICIPANT - earnedToday);
    }

    /**
     * 운동 기록 하나로 달라진 팀 경험치를 조회한다. 결과 화면용.
     */
    @Transactional(readOnly = true)
    public TeamXpChangeResponse getChange(Workout workout) {
        Team team = teamOf(workout);
        if (!workout.isFinished()) {
            return TeamXpChangeResponse.notAccrued(XpStatus.NOT_FINISHED, team);
        }
        if (team == null) {
            return TeamXpChangeResponse.notAccrued(XpStatus.NO_TEAM, null);
        }

        Optional<TeamXpLog> log = teamXpLogRepository.findByWorkoutId(workout.getId());
        if (log.isEmpty()) {
            return TeamXpChangeResponse.notAccrued(XpStatus.FORMULA_UNDECIDED, team);
        }
        return TeamXpChangeResponse.accrued(team, log.get().getEarnedXp(), teamRepository.findTotalXp(team.getId()),
                log.get().getTeamTotalXpAfter());
    }

    /**
     * 팀 누적 경험치를 조회한다.
     *
     * @throws CustomException 팀이 없으면 {@link ErrorCode#TEAM_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public TeamXpResponse getTeamXp(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        return TeamXpResponse.of(team, xpCalculator.isDecided(), teamRepository.findTotalXp(teamId));
    }

    /** 운동한 운동방의 팀. 운동방 없이 운동했으면 null */
    private Team teamOf(Workout workout) {
        return workout.getRoom() == null ? null : workout.getRoom().getTeam();
    }
}
