# FITMATE Backend

친구들과 함께 작심삼일을 운동 습관으로 바꾸는 AI 소셜 홈트레이닝 서비스의 백엔드입니다.
2026 멋쟁이사자처럼 1호선톤 출품용 데스크톱 웹 MVP입니다.

## 문서

| 문서 | 내용 |
|---|---|
| [CONTEXT.md](CONTEXT.md) | 용어집. 코드와 문서의 이름은 여기를 따른다 |
| [docs/PRD.md](docs/PRD.md) | 제품 요구사항, 사용자 스토리, 범위 밖, PM 확인 필요 항목 |
| [docs/기능명세서.md](docs/기능명세서.md) | 요구사항 9개, 기능 22개의 슬롯별 명세 |
| [docs/ERD.md](docs/ERD.md) | 테이블 10개와 각 테이블의 근거 기능 |
| [docs/슬라이스_역할분담.md](docs/슬라이스_역할분담.md) | 슬라이스 9개, 의존 순서, 직군별 역할 |

원본 기획은 PM이 매니패스트에 작성했다. 문서끼리 어긋나면 PM 확인 후 원본과 이 문서를 함께 고친다.

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1.2 |
| 빌드 | Gradle |
| API | Spring Web MVC |
| 실시간 | Spring WebSocket |
| 데이터 | Spring Data JPA, Validation |
| 운영 | Spring Boot Actuator |
| 로컬 DB | H2 |
| 운영 DB | 미정 (MySQL 또는 PostgreSQL) |
| API 문서 | springdoc-openapi (추가 예정) |

## 구현 현황

착수 전이다. 진행 상황은 슬라이스 이슈에서 관리한다.

## 프로젝트 구조

```
com.team20.fitmate
├── FitmateApplication.java
│
├── global                          # 도메인 공통
│   ├── config                      # WebConfig(CORS), SwaggerConfig, JpaAuditingConfig, WebSocketConfig
│   ├── common                      # BaseTimeEntity
│   ├── rsdata                      # RsData<T>
│   └── exception                   # CustomException, ErrorCode, GlobalExceptionHandler
│
├── domain
│   ├── participant                 # 참가자 · 임시 식별 · 닉네임 · 캐릭터     [슬라이스 2]
│   ├── room                        # 운동방 · 팀                              [슬라이스 2, 8]
│   ├── exercise                    # 운동 목록 · 측정 방식 · 가이드 참조      [슬라이스 1, 5]
│   ├── routine                     # 루틴 · 첫 회차 추천 · 2회차 추천           [슬라이스 1, 9]
│   ├── workout                     # 운동 기록 · 수행 기록 · 결과 조회          [슬라이스 7, 8]
│   ├── teamxp                      # 팀 경험치 적립 · 중복 방지                 [슬라이스 8]
│   └── cheer                       # 응원 이벤트 저장                          [슬라이스 6]
│
└── realtime                        # 운동방 실시간 공유 — 해석하지 않고 전달만 한다
    ├── handler                     # WebSocket 연결 · 메시지 수신
    ├── room                        # 방별 참가자 상태 · 연결 상태
    └── message                     # 입퇴장 · 진행 상태 · 동작 정보 · 응원 메시지 형식
```

- 각 `domain` 패키지 내부는 `controller / service / repository / entity / dto`로 나눈다.
- 로그인이 없으므로 `security` 패키지를 두지 않는다.
- `realtime`은 다른 참가자의 상태로 개인 운동 진행을 바꾸는 경로를 만들지 않는다.
- 동작 정보와 현재 진행 상태는 `realtime`에서만 다루고 DB에 저장하지 않는다. 확정된 결과만 `domain`으로 넘긴다.
- 추천 규칙은 AI 담당이 설계하므로 `routine` 안에서 인터페이스 뒤에 둔다. 규칙이 바뀌어도 API는 그대로 둔다.

## 실행 방법

### 1. 환경 변수

아직 없다. 운영 DB가 정해지면 `.env.example`을 추가하고 여기에 적는다.

### 2. 로컬 실행

```bash
./gradlew bootRun
```

- 로컬은 H2를 쓴다.
- 헬스 체크: `GET http://localhost:8080/actuator/health`

### 3. 배포

미정이다.

---

## 팀 컨벤션

### 브랜치 전략

| 브랜치 | 용도 |
|---|---|
| `main` | 배포 |
| `develop` | 통합. **PR은 여기로 보낸다** |
| `{라벨}/#{이슈번호}-{이름}` | 작업 브랜치. 예) `Feat/#3-donghwan` |

- **이슈 하나당 브랜치 하나.**
- 작업 브랜치는 `develop`에서 따고, PR도 `develop`으로 보낸다.
- 이슈와 브랜치는 **담당자가 직접 만든다.**
- 라벨은 커밋 라벨과 같은 대소문자를 쓴다. `feat/#3`과 `Feat/#3`을 섞지 않는다.

### 커밋 메시지

```
[{라벨}/#{이슈번호}] {라벨}: {한글 설명}
```

예시:

```
[Feat/#3] Feat: 목표별 첫 루틴 추천 API 구현
[Test/#3] Test: 루틴 추천 서비스 테스트 추가
[Fix/#12] Fix: 재접속 시 유효 횟수가 중복 기록되던 문제 수정
```

| 라벨 | 의미 |
|---|---|
| `Feat` | 새로운 기능 |
| `Fix` | 버그 수정 |
| `Refactor` | 기능 변화 없는 구조 개선 |
| `Design` | UI/응답 형태 변경 |
| `Chore` | 빌드·의존성 |
| `Setting` | 환경 설정 |
| `Comment` | 주석·로그 |
| `Docs` | 문서 |
| `Test` | 테스트 |

- **하나의 기능이 끝날 때마다 커밋한다.** 의미 단위로 잘게 쪼개되, 각 커밋은 빌드가 되어야 한다.

### 이슈 양식

이슈를 만들 때 템플릿을 고른다. 공통 이슈는 [issue.md](.github/ISSUE_TEMPLATE/issue.md), 슬라이스는 [slice.md](.github/ISSUE_TEMPLATE/slice.md)다.

```markdown
제목: [{라벨}] {목표 한 줄}

## 목적

## 범위
### 포함:
### 제외:

## 작업 내용
- [ ]

## 완료 조건
- [ ]
```

> 슬라이스 이슈 9개는 [docs/슬라이스_역할분담.md](docs/슬라이스_역할분담.md)에 본문까지 작성되어 있다. 제목은 `[Feat] Slice 0N {할 수 있게 되는 일}`이다.

라벨은 GitHub 기본 라벨에 아래를 더한다.

| 라벨 | 의미 |
|---|---|
| `ready-for-agent` | 사람 개입 없이 바로 구현 착수 가능 |
| `ready-for-human` | 사람 판단이 필요함 |
| `blocked` | 선행 슬라이스에 막혀 있음 |

### PR 양식

[pull_request_template.md](.github/pull_request_template.md)가 PR 본문에 자동으로 들어간다.

```markdown
제목: [{라벨}/#{이슈번호}] {라벨}: {작업 요약}

## 연관 Issue
- closes #{Issue 번호}

## 요약
이 PR에서 변경한 내용을 간단히 설명합니다.

## 변경 사항
- 변경 사항 1
- 변경 사항 2

## 범위
### 포함:
- 예시 1
### 제외:
- 예시 1

## 스크린샷 / 미리 보기
- before/after 이미지 또는 캡처 첨부
```

- **임의 섹션(API 명세, 리뷰 포인트 등)을 추가하지 않는다.** 필요하면 요약·변경 사항 안에 녹인다.
- 머지 전 **팀원 1명 이상 리뷰**를 받는다.
- 배포 PR은 `[Release] develop → main 통합 배포`로 올린다.

### 작업 진행 로그

작업을 멈추거나 한 단계를 마치면 해당 이슈에 댓글을 남긴다 — **한 것 / 남은 것 / 다음에 할 것 / 막힌 것.**

---

## 코드 컨벤션

### 계층

```
Controller  요청/응답 변환만. 비즈니스 로직 금지
Service     로직 · 트랜잭션
Repository  DB 접근
```

### 응답과 예외

- 모든 REST 응답은 `RsData<T>`로 감싼다.
- 예외는 `CustomException` + `ErrorCode`를 던지고 `GlobalExceptionHandler`가 처리한다.
- **내부 예외 메시지를 그대로 응답에 노출하지 않는다.**
- **측정하지 못한 값은 `0`이나 추정값으로 채우지 않는다.** 미측정은 `null`과 상태값으로 전달한다.

### 엔티티와 DTO

- 엔티티 → DTO 변환은 DTO의 정적 메서드 `from()`
- DTO → 엔티티 변환은 `toEntity()`
- **엔티티에 `@Setter`를 붙이지 않는다.** 의미 있는 메서드로 상태를 바꾼다 (`finish()`, `addValidCount()` 등)
- 공통 시간 컬럼은 `BaseTimeEntity` 상속

### 트랜잭션

- 조회 전용 서비스 메서드는 `@Transactional(readOnly = true)`
- LAZY 연관을 컨트롤러에서 건드리면 500이 난다. **DTO 변환은 서비스 트랜잭션 안에서 끝낸다.**

### 주석과 문서

- **모든 코드에 한글 주석을 단다.** 각 클래스·메서드가 무슨 역할인지 읽고 바로 알 수 있게.
- **새 엔드포인트에는 Swagger 어노테이션을 전부 붙인다** (`@Tag`, `@Operation`, `@ApiResponses`). 나중에 몰아서 하면 안 붙는다.

### 파라미터

- `@PathVariable`, `@RequestParam`에 **이름을 반드시 명시한다.**
  ```java
  // O
  @PathVariable("roomId") Long roomId
  // X — IntelliJ 실행 시 500
  @PathVariable Long roomId
  ```

### 용어

[CONTEXT.md](CONTEXT.md)의 용어를 따른다. 변수명·클래스명·주석·커밋 메시지 전부 해당된다.

| 쓸 것 | 쓰지 말 것 |
|---|---|
| 참가자 `Participant` | 회원, 유저, 멤버 |
| 운동방 `Room` | 로비, 채팅방, 세션 |
| 루틴 `Routine` | 플랜, 프로그램 |
| 운동 기록 `Workout` | 세션, 운동 로그 |
| 수행 기록 `ExerciseRecord` | 점수, 성적 |
| 유효 횟수 `validCount` | 카운트, 점수 |
| 동작 정보 `Pose` | 모션, 영상 |
| AI 가이드 `Guide` | 코치 캐릭터, 시범 영상 |
| 응원 `Cheer` | 좋아요, 알림 |
| 팀 경험치 `TeamXp` | 포인트, 랭킹 |

> 특히 **운동 기록**과 **수행 기록**을 섞지 않는다. 운동 기록은 회차 단위, 수행 기록은 그 안의 운동 하나 단위다.

---

## 작업 분배

| 트랙 | 담당 | 맡는 일 |
|---|---|---|
| A. 실시간 | (미정) | 운동방 입퇴장, 참가자 상태, 동작 정보 전달, 응원 전달, 끊김과 재접속 — 슬라이스 2·4·6·7 |
| B. 데이터 · 배포 | (미정) | 참가자 식별, 루틴 저장, 수행 기록, 결과 조회, 팀 경험치, 2회차 저장, 배포 — 슬라이스 1·2·6·7·8·9 |

- A/B가 마주치는 접점은 **실시간 메시지 형식**과 **운동 기록 확정 시점**뿐이다. 슬라이스 2와 7에서 확정한 뒤 바꾸지 않는다.
- AI, 3D, 프론트, 디자인 분담은 [docs/슬라이스_역할분담.md](docs/슬라이스_역할분담.md)를 따른다.
