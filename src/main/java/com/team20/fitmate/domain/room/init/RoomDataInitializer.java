package com.team20.fitmate.domain.room.init;

import com.team20.fitmate.domain.room.config.RoomProperties;
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

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 서버 시작 때 지정 운동방과 시연용 운동방들을 만든다. 운동방마다 팀을 하나씩 둔다(방 = 팀).
 * <p>
 * 이름으로 이미 있는지 확인하므로 여러 번 시작해도 같은 방이 또 생기지 않는다.
 * 운영 DB에 지정 운동방만 있던 경우에도 시연용 운동방(`운동방 1` …)만 더해진다.
 * <p>
 * <b>임시값이다.</b> 정원 5명은 기획서의 "친구 2~5명"을 근거로 했고, 시연용 방 수는 {@code room.seed-count}로 바꾼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomDataInitializer implements ApplicationRunner {

    /** 지정 운동방 이름 (임시) */
    static final String DESIGNATED_ROOM_NAME = "FITMATE 운동방";

    /** 지정 운동방의 팀 이름 (임시) */
    static final String DESIGNATED_TEAM_NAME = "FITMATE 팀";

    /** 시연용 운동방 이름 앞부분. 뒤에 번호가 붙는다 */
    static final String SEED_ROOM_NAME_PREFIX = "운동방 ";

    private final TeamRepository teamRepository;
    private final RoomRepository roomRepository;
    private final RoomProperties roomProperties;

    /**
     * 이름이 같은 방이 없을 때만 지정 운동방과 시연용 운동방을 저장한다.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existingNames = roomRepository.findAll().stream()
                .map(Room::getName)
                .collect(Collectors.toSet());

        int created = 0;
        if (!existingNames.contains(DESIGNATED_ROOM_NAME)) {
            createRoom(DESIGNATED_ROOM_NAME, DESIGNATED_TEAM_NAME);
            created++;
        }
        for (int i = 1; i <= roomProperties.seedCount(); i++) {
            String name = SEED_ROOM_NAME_PREFIX + i;
            if (!existingNames.contains(name)) {
                createRoom(name, teamNameOf(name));
                created++;
            }
        }
        if (created > 0) {
            log.info("운동방 초기 데이터 {}개 적재", created);
        }
    }

    /** 팀과 운동방을 함께 만든다 */
    private void createRoom(String roomName, String teamName) {
        Team team = teamRepository.save(Team.create(teamName));
        roomRepository.save(Room.create(team, roomName, roomProperties.capacity()));
    }

    /**
     * 운동방 이름으로 팀 이름을 만든다. 방 만들기 API에서도 같은 규칙을 쓴다.
     */
    public static String teamNameOf(String roomName) {
        return roomName + " 팀";
    }
}
