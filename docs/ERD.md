# FITMATE ERD

> 스택 중립 초안. 타입은 논리 타입이며 DB가 정해지면 바꾼다. 기능명세서의 `데이터` 항목을 근거로 했고, **제안**으로 표시한 것은 PM 문서에 없는 설계 판단이다.

```mermaid
erDiagram
    TEAM ||--o{ ROOM : "운영"
    ROOM ||--o{ PARTICIPANT : "입장"
    PARTICIPANT ||--o{ ROUTINE : "추천받음"
    ROUTINE ||--|{ ROUTINE_EXERCISE : "구성"
    EXERCISE ||--o{ ROUTINE_EXERCISE : "포함"
    ROUTINE ||--o| WORKOUT : "수행"
    PARTICIPANT ||--o{ WORKOUT : "기록"
    ROOM ||--o{ WORKOUT : "진행"
    PUBLIC_PARTY ||--o{ WORKOUT : "공개 파티 진행, 제안"
    WORKOUT ||--o{ EXERCISE_RECORD : "운동별 기록"
    ROUTINE_EXERCISE ||--o| EXERCISE_RECORD : "목표 대비"
    WORKOUT |o--o{ ROUTINE : "다음 회차 근거"
    WORKOUT ||--o| TEAM_XP_LOG : "적립"
    TEAM ||--o{ TEAM_XP_LOG : "누적"
    PARTICIPANT ||--o{ TEAM_XP_LOG : "기여"
    ROOM ||--o{ CHEER : "발생"
    PARTICIPANT ||--o{ CHEER : "보냄"
    PARTICIPANT ||--o{ CHEER : "받음"

    TEAM {
        id id PK
        string name "팀 이름"
        int total_xp "누적 경험치"
        int level "NULL 허용, 기준 합의 전 미사용"
    }
    ROOM {
        id id PK
        id team_id FK
        string name "운동방 이름"
        int capacity "정원, 확인 필요"
    }
    PARTICIPANT {
        id id PK "임시 식별값"
        id room_id FK
        string client_token UK "기기 보관 토큰, 제안"
        string nickname "중복 허용"
        string character_code "캐릭터 선택 결과"
        datetime created_at
    }
    EXERCISE {
        id id PK "운동 ID"
        string name "운동 이름"
        string measure_type "COUNT 또는 TIME, 제안"
        string guide_motion_ref "가이드 시범 동작 참조"
        boolean recognition_verified "인식 검증 여부, 제안"
    }
    ROUTINE {
        id id PK
        id participant_id FK
        int round_no "회차"
        string goal "목표 4종"
        id based_on_workout_id FK "NULL이면 첫 회차"
        string adjustment_type "UP, KEEP, UNAVAILABLE, 제안"
        string adjustment_reason "조정 사유"
        string status "READY, FAILED, 제안"
        datetime created_at
    }
    ROUTINE_EXERCISE {
        id id PK
        id routine_id FK
        id exercise_id FK
        int sort_order "순서"
        int target_count "횟수형만"
        int target_seconds "시간형만"
        int target_sets "세트"
    }
    PUBLIC_PARTY {
        id id PK "공개 파티 식별값"
        string name "파티 이름"
        datetime open_at "오픈 시각, 설정값"
        datetime close_at "종료 시각, 설정값"
    }
    WORKOUT {
        id id PK "운동 기록 식별값"
        id routine_id FK "공개 파티는 NULL 가능, 제안"
        id participant_id FK
        id room_id FK "친구 파티, 공개 파티면 NULL"
        id public_party_id FK "공개 파티, 친구 파티면 NULL, 제안"
        string status "IN_PROGRESS, FINISHED, 제안"
        string analysis_status "ANALYZED, INSUFFICIENT, FAILED, 제안"
        datetime started_at
        datetime finished_at
    }
    EXERCISE_RECORD {
        id id PK
        id workout_id FK
        id routine_exercise_id FK
        int valid_count "인정 횟수, 미측정은 NULL"
        int actual_seconds "실제 소요 시간"
        int performed_sets "수행 세트, 제안"
        string posture_result "자세 판정 결과, 형식 확인 필요"
    }
    TEAM_XP_LOG {
        id id PK
        id team_id FK
        id participant_id FK
        id workout_id FK,UK "중복 적립 방지"
        int earned_xp "적립 경험치, 산식 미정"
        datetime created_at
    }
    CHEER {
        id id PK
        id room_id FK
        id sender_id FK "보낸 사람"
        id receiver_id FK "받은 사람"
        string reaction "리액션"
        datetime created_at "이벤트 시각"
    }
```

## 테이블 근거

| 테이블 | 근거 기능 | 비고 |
|---|---|---|
| `TEAM` | 9.1 | 팀 정의가 미정이라 한 운동방에 팀 하나를 임시로 가정한다 |
| `ROOM` | 2.1, 4.1 | 시연에서는 미리 넣어 둔 한 행을 쓴다 |
| `PARTICIPANT` | 2.2 | 닉네임은 유니크가 아니다. 같은 이름이어도 기록을 혼동하지 않아야 한다 |
| `EXERCISE` | 1.2, 3.2, 5.1 | 운동 목록이 미정이라 행은 나중에 넣는다 |
| `ROUTINE` | 1.2, 8.2 | 첫 회차와 2회차를 같은 구조로 저장한다 |
| `ROUTINE_EXERCISE` | 1.2, 1.3 | 횟수형과 시간형 중 하나만 채운다 |
| `WORKOUT` | 7.1, 8.1, 8.3, 9.1, 10.1 | 한 회차 수행 단위. 이 ID로 경험치 중복을 막는다. 성장 기록(8.3)은 별도 테이블 없이 이 테이블과 `EXERCISE_RECORD`, `ROUTINE`을 참가자 기준으로 모아 조회한다 |
| `EXERCISE_RECORD` | 3.2, 8.1 | 운동별 수행 기록. 플랭크는 `actual_seconds`로 기록한다 |
| `TEAM_XP_LOG` | 9.1 | `workout_id` 유니크로 중복 적립을 DB에서 막는다 |
| `CHEER` | 6.1 | 응원 이벤트 기록 |
| `PUBLIC_PARTY` | 2.4, 10.1 | **제안.** 공개 선셋 파티(P1). 오픈·종료 시각은 설정값이며 정원이 없다. 친구 파티 `ROOM`과 섞지 않는다 |

## DB에 저장하지 않는 값 (제안)

아래는 자주 바뀌는 실시간 값이라 실시간 계층에서만 다루고, 확정된 결과만 DB에 남긴다.

| 값 | 근거 기능 | 처리 |
|---|---|---|
| 동작 정보 (관절 좌표) | 5.2 | 같은 방 참가자에게 전달만 한다 |
| 현재 운동, 진행 상태 | 4.1, 4.2 | 방의 참가자 상태로 유지한다 |
| 연결 상태 | 4.4 | 끊기면 연결 불안정으로 표시한다 |
| 카메라 원본 영상 | 3.1 | 저장 대상에서 제외한다. 처리 위치는 미정 |

## ERD를 바꿀 수 있는 결정

| 결정 | 결정권 | 바뀌는 곳 |
|---|---|---|
| 운동방과 팀의 관계 | 팀 합의 | `TEAM`과 `ROOM` 관계 |
| 인원수와 정원 | PM | `ROOM.capacity` |
| 5일 테스트의 임시 식별 유지 방식 | PM, 백엔드 | `PARTICIPANT.client_token` |
| 자세 결과 형식 (점수, 등급, 교정 횟수) | PM, AI | `EXERCISE_RECORD.posture_result` |
| 경험치 산식과 레벨 구간 | 팀 합의 | `TEAM.level`, `TEAM_XP_LOG.earned_xp` |
| 경험치 적립 단위 (회차, 운동별) | 팀 합의 | `TEAM_XP_LOG.workout_id` |
| 난이도, 주요 부위, 예상 시간 표시 여부 | PM | `EXERCISE`, `ROUTINE`에 컬럼 추가 |
| 수행 부족 시 유지 또는 하향 | PM, AI | `ROUTINE.adjustment_type` |
| 응원 기록의 저장 기간 | PM | `CHEER` 유지 여부 |
| 공개 파티 운영 시각·요일, 팀 경험치 적립 여부 | PM | `PUBLIC_PARTY`, `TEAM_XP_LOG` 적용 범위 |
| 공개 파티 에어로빅의 판정 방식(횟수형·시간형) | PM, AI | `EXERCISE.measure_type`, `EXERCISE_RECORD` |
| 회차마다 목표를 다시 고를 수 있는지 | PM | `ROUTINE.goal` |
