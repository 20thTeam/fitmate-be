package com.team20.fitmate.domain.room.init;

import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.room.repository.RoomRepository;
import com.team20.fitmate.domain.room.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버 시작 때 지정 운동방과 그 팀을 하나 만든다. 운동방이 이미 있으면 아무것도 하지 않는다.
 * <p>
 * <b>임시값이다.</b> 운동방 하나 = 팀 하나는 이슈 #2의 임시 가정이고,
 * 정원 5명은 기획서의 "친구 2~5명"을 근거로 했다. PM이 인원수를 확정하면 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomDataInitializer implements ApplicationRunner {

    /** 지정 운동방 이름 (임시) */
    static final String DESIGNATED_ROOM_NAME = "FITMATE 운동방";

    /** 지정 운동방의 팀 이름 (임시) */
    static final String DESIGNATED_TEAM_NAME = "FITMATE 팀";

    /** 정원 (임시, 기획서 "친구 2~5명") */
    static final int DESIGNATED_ROOM_CAPACITY = 5;

    private final TeamRepository teamRepository;
    private final RoomRepository roomRepository;

    /**
     * 운동방이 하나도 없을 때만 지정 운동방과 팀을 저장한다.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (roomRepository.count() > 0) {
            return;
        }
        Team team = teamRepository.save(Team.create(DESIGNATED_TEAM_NAME));
        roomRepository.save(Room.create(team, DESIGNATED_ROOM_NAME, DESIGNATED_ROOM_CAPACITY));
        log.info("지정 운동방 초기 데이터 적재");
    }
}
