package com.team20.fitmate.domain.routine.service;

import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.routine.dto.RoutineExerciseResponse;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.entity.RoutineStatus;
import com.team20.fitmate.domain.routine.recommend.RecommendedExercise;
import com.team20.fitmate.domain.routine.recommend.RoutineRecommender;
import com.team20.fitmate.domain.routine.repository.RoutineRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RoutineService}의 첫 회차 추천·저장과 조회를 확인하는 테스트.
 * <p>
 * 추천 규칙은 {@link SwitchableRecommender}로 감싸서, 기본 규칙과 추천 불가 상황을 테스트마다 바꿔 쓴다.
 */
@SpringBootTest
@Transactional
class RoutineServiceTest {

    @Autowired
    private RoutineService routineService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private SwitchableRecommender recommender;

    private String clientToken;

    @BeforeEach
    void setUp() {
        recommender.reset();
        clientToken = participantService.create(new ParticipantCreateRequest("아현", "CHARACTER_01")).clientToken();
    }

    @ParameterizedTest
    @EnumSource(Goal.class)
    @DisplayName("목표를 고르면 운동 2~3개를 순서대로 담은 추천 완료 루틴이 참가자에 연결되어 저장된다")
    void createFirstRound(Goal goal) {
        RoutineResponse response = routineService.createFirstRound(clientToken, goal);

        assertThat(response.status()).isEqualTo(RoutineStatus.COMPLETED);
        assertThat(response.roundNo()).isEqualTo(1);
        assertThat(response.goal()).isEqualTo(goal);
        assertThat(response.exercises()).hasSizeBetween(2, 3);
        assertThat(response.exercises()).extracting(RoutineExerciseResponse::sortOrder)
                .containsExactly(java.util.stream.IntStream.rangeClosed(1, response.exercises().size())
                        .boxed().toArray(Integer[]::new));

        Routine saved = routineRepository.findById(response.routineId()).orElseThrow();
        assertThat(saved.getParticipant().getClientToken()).isEqualTo(clientToken);
        assertThat(saved.getRoutineExercises()).hasSameSizeAs(response.exercises());
    }

    @Test
    @DisplayName("횟수형은 목표 횟수만, 시간형은 목표 시간만 채운다")
    void measureTypeFields() {
        RoutineResponse response = routineService.createFirstRound(clientToken, Goal.STRENGTH);

        assertThat(response.exercises()).allSatisfy(exercise -> {
            if (exercise.measureType() == MeasureType.COUNT) {
                assertThat(exercise.targetCount()).isPositive();
                assertThat(exercise.targetSeconds()).isNull();
            } else {
                assertThat(exercise.targetSeconds()).isPositive();
                assertThat(exercise.targetCount()).isNull();
            }
        });
    }

    @Test
    @DisplayName("추천 규칙이 인식 검증된 운동에 없는 운동을 고르면 운동 목록 없이 추천 불가로 저장한다")
    void unavailableWhenUnknownExercise() {
        recommender.override(List.of(
                new RecommendedExercise("SQUAT", 15, 3),
                new RecommendedExercise("없는_운동", 10, 3)));

        RoutineResponse response = routineService.createFirstRound(clientToken, Goal.STRENGTH);

        assertThat(response.status()).isEqualTo(RoutineStatus.UNAVAILABLE);
        assertThat(response.exercises()).isEmpty();
        assertThat(routineRepository.findById(response.routineId()).orElseThrow().getRoutineExercises()).isEmpty();
    }

    @Test
    @DisplayName("추천 운동 수가 2~3개가 아니면 추천 불가로 저장한다")
    void unavailableWhenWrongSize() {
        recommender.override(List.of(new RecommendedExercise("SQUAT", 15, 3)));

        RoutineResponse response = routineService.createFirstRound(clientToken, Goal.STRENGTH);

        assertThat(response.status()).isEqualTo(RoutineStatus.UNAVAILABLE);
        assertThat(response.exercises()).isEmpty();
    }

    @Test
    @DisplayName("저장한 본인 루틴을 같은 순서와 수행량으로 조회한다")
    void get() {
        RoutineResponse created = routineService.createFirstRound(clientToken, Goal.WEIGHT_LOSS);

        RoutineResponse found = routineService.get(clientToken, created.routineId());

        assertThat(found).isEqualTo(created);
    }

    @Test
    @DisplayName("다른 참가자의 루틴은 ROUTINE_NOT_FOUND로 막는다")
    void getOthersRoutine() {
        RoutineResponse created = routineService.createFirstRound(clientToken, Goal.WEIGHT_LOSS);
        String otherToken = participantService.create(new ParticipantCreateRequest("아현", "CHARACTER_01"))
                .clientToken();

        assertThatThrownBy(() -> routineService.get(otherToken, created.routineId()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROUTINE_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 토큰으로 추천받으면 PARTICIPANT_NOT_FOUND 예외가 난다")
    void createWithUnknownToken() {
        assertThatThrownBy(() -> routineService.createFirstRound("없는-토큰", Goal.STRENGTH))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARTICIPANT_NOT_FOUND);
    }

    /**
     * 테스트용 추천 규칙 설정. 기본 규칙을 감싼 {@link SwitchableRecommender}를 우선 사용한다.
     */
    @TestConfiguration
    static class RecommenderConfig {

        @Bean
        @Primary
        SwitchableRecommender switchableRecommender(RoutineRecommender defaultRoutineRecommender) {
            return new SwitchableRecommender(defaultRoutineRecommender);
        }
    }

    /**
     * 평소에는 기본 규칙을 쓰고, {@link #override}로 정한 결과가 있으면 그 결과를 돌려주는 추천 규칙.
     */
    static class SwitchableRecommender implements RoutineRecommender {

        private final RoutineRecommender delegate;
        private final AtomicReference<List<RecommendedExercise>> overridden = new AtomicReference<>();

        SwitchableRecommender(RoutineRecommender delegate) {
            this.delegate = delegate;
        }

        /** 다음 추천부터 돌려줄 결과를 정한다 */
        void override(List<RecommendedExercise> result) {
            overridden.set(result);
        }

        /** 기본 규칙으로 되돌린다 */
        void reset() {
            overridden.set(null);
        }

        @Override
        public List<RecommendedExercise> recommendFirstRound(Goal goal) {
            List<RecommendedExercise> result = overridden.get();
            return result != null ? result : delegate.recommendFirstRound(goal);
        }
    }
}
