package com.team20.fitmate.domain.exercise.controller;

import com.team20.fitmate.domain.exercise.dto.ExerciseResponse;
import com.team20.fitmate.domain.exercise.service.ExerciseService;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운동 API. 운동방에서 현재 운동의 AI 가이드 시범을 찾을 때 쓴다.
 */
@Tag(name = "운동", description = "운동 정보와 AI 가이드 시범 참조")
@RestController
@RequestMapping("/api/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    /**
     * 운동 코드로 운동 하나를 조회한다.
     */
    @Operation(summary = "운동 조회 (가이드 시범 참조 포함)",
            description = "루틴 응답의 exerciseCode로 조회한다. 시범이 없으면 guideStatus가 NONE이고 guideMotionRef는 null이다. "
                    + "이때 다른 운동의 시범을 대신 보여주지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공 (시범이 없어도 200)"),
            @ApiResponse(responseCode = "404", description = "404-7 없는 운동 코드")
    })
    @GetMapping("/{exerciseCode}")
    public RsData<ExerciseResponse> get(
            @Parameter(description = "운동 코드 (예: SQUAT)", required = true)
            @PathVariable("exerciseCode") String exerciseCode) {
        return RsData.ok("운동을 조회했습니다.", exerciseService.get(exerciseCode));
    }
}
