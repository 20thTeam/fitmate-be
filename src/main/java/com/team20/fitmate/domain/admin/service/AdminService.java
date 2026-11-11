package com.team20.fitmate.domain.admin.service;

import com.team20.fitmate.domain.admin.dto.AdminParticipantResponse;
import com.team20.fitmate.domain.admin.dto.AdminResetResponse;
import com.team20.fitmate.domain.admin.dto.AdminRoomParticipantResponse;
import com.team20.fitmate.domain.admin.dto.AdminRoomResponse;
import com.team20.fitmate.domain.admin.dto.AdminWorkoutResponse;
import com.team20.fitmate.domain.admin.repository.AdminQueryRepository;
import com.team20.fitmate.domain.admin.repository.DemoDataResetter;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.xp.calculator.XpCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 운영 화면(/admin) 조회와 시연 데이터 초기화. 시연 중 방 접속자와 전체 참가자·운동 기록을 한눈에 보게 한다.
 * <p>
 * 운영 키 확인은 인터셉터가 먼저 끝낸다. 이 서비스는 참가자 구분 없이 전체를 읽는다.
 * 측정하지 못한 값과 확정 전 값은 0으로 채우지 않고 null로 둔다.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminQueryRepository adminQueryRepository;
    private final DemoDataResetter demoDataResetter;
    private final RoomPresenceReader roomPresenceReader;
    private final XpCalculator xpCalculator;

    /**
     * 운동방별로 REST 입장한 참가자와 지금 웹소켓 연결 여부를 조회한다.
     */
    @Transactional(readOnly = true)
    public List<AdminRoomResponse> getRooms() {
        Map<Long, List<Participant>> participantsByRoom = adminQueryRepository.findParticipantsInRooms().stream()
                .collect(Collectors.groupingBy(participant -> participant.getRoom().getId()));

        return adminQueryRepository.findAllRoomsWithTeam().stream()
                .map(room -> {
                    Set<Long> connectedIds = roomPresenceReader.connectedParticipantIds(room.getId());
                    List<AdminRoomParticipantResponse> participants = participantsByRoom
                            .getOrDefault(room.getId(), List.of()).stream()
                            .map(participant -> AdminRoomParticipantResponse.of(
                                    participant, connectedIds.contains(participant.getId())))
                            .toList();
                    return AdminRoomResponse.of(room, participants);
                })
                .toList();
    }

    /**
     * 전체 참가자를 최근 생성 순으로 조회한다. 재방문 토큰은 응답에 담지 않는다.
     */
    @Transactional(readOnly = true)
    public List<AdminParticipantResponse> getParticipants() {
        Map<Long, Long> workoutCounts = adminQueryRepository.countFinishedWorkoutsByParticipant();
        return adminQueryRepository.findAllParticipantsLatestFirst().stream()
                .map(participant -> AdminParticipantResponse.of(
                        participant, workoutCounts.getOrDefault(participant.getId(), 0L)))
                .toList();
    }

    /**
     * 운동 기록을 최근 시작 순으로 조회한다. 진행 중 기록도 포함한다.
     * <p>
     * 경험치 산식이 정해지지 않았으면 성장 기록과 같게 경험치를 null로 둔다.
     *
     * @param participantId 이 참가자 기록만 본다. null이면 전체. 없는 참가자면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<AdminWorkoutResponse> getWorkouts(Long participantId) {
        List<Workout> workouts = adminQueryRepository.findWorkoutsLatestFirst(participantId);
        List<Long> workoutIds = workouts.stream().map(Workout::getId).toList();
        Map<Long, Integer> validCounts = adminQueryRepository.sumMeasuredValidCountByWorkout(workoutIds);
        Map<Long, Integer> earnedXps = xpCalculator.isDecided()
                ? adminQueryRepository.findEarnedXpByWorkout(workoutIds)
                : Map.of();

        return workouts.stream()
                .map(workout -> AdminWorkoutResponse.of(workout,
                        validCounts.get(workout.getId()),
                        earnedXps.get(workout.getId())))
                .toList();
    }

    /**
     * 시연 데이터를 초기화한다. 운동방·팀은 남긴다.
     * <p>
     * DB 삭제가 커밋된 뒤 실시간 연결을 모두 닫는다. 삭제가 실패하면 연결은 그대로 둔다.
     * 초기화 뒤 기존 토큰으로 참가자를 조회하면 404-2가 되고, 프론트는 프로필을 다시 만든다.
     * 이 메서드에는 트랜잭션을 걸지 않는다(삭제 트랜잭션이 끝난 뒤 연결을 닫기 위해).
     */
    public AdminResetResponse reset() {
        AdminResetResponse result = demoDataResetter.reset();
        roomPresenceReader.disconnectAll();
        return result;
    }
}
