# travelMaker 백엔드 PRD (구현 단위와 순서)

목표, MVP 범위, 코딩 규칙은 `../CLAUDE.md`를 따른다. 이 문서는 "무엇을 어떤 단위로, 어떤 순서로 구현하는가"만 다룬다.
기준 문서는 Notion의 DB 모델링(테이블 11개)과 API 명세(엔드포인트 31개)이다.

## 1. 작업 단위 정하기

### 1.1 단위 구분

- **구현 단위(Unit)**: 같은 도메인의 테이블과 API를 묶은 단위. 이슈 1개 이상, 브랜치 `feature/<이슈번호>`로 진행한다.
- **작업 단계(Step)**: 한 Unit 안에서 커밋을 나누는 기준이다. 아래 순서를 따른다.
  1. 엔티티 (`feat:`)
  2. Repository + Repository 테스트 (`feat:` / `test:`)
  3. DTO + Service (`feat:`)
  4. Controller + API 테스트 (`feat:` / `test:`)
- 한 PR은 Unit 하나 또는 그 일부(테이블 1개, API 1~3개)로 작게 유지한다.

### 1.2 Unit 목록

| Unit | 이름 | 테이블 | API (개수) | 선행 Unit |
| --- | --- | --- | --- | --- |
| U0 | 공통 기반 | - | - | - |
| U1 | 인증·사용자 | users | auth 2, users 3 (5) | U0 |
| U2 | 취향 프로파일 | user_preference_profiles | preference 2 (2) | U1 |
| U3 | 여행·참여자 | trips, trip_members | trips/members 7 (7) | U1 |
| U4 | 1:1 초대 | trip_invitations | invitations 6 (6) | U3 |
| U5 | 장소 | places | places/search 1 (1) | U0 |
| U6 | 일정 | itineraries, itinerary_days, itinerary_items | itineraries 7 (7) | U3, U5 |
| U7 | AI 일정 생성 | trip_member_snapshots, generation_jobs | generation-jobs 3 (3) | U2, U3, U6 |
| U8 | 통합 마무리 | - | - | U1~U7 |

- 테이블 11개와 API 31개가 빠짐없이 한 Unit에 들어 있다. (5+2+7+6+1+7+3 = 31)
- U1~U4는 MVP 흐름 1~4단계, U5~U7은 5단계(AI 일정 생성·확정)에 해당한다.

## 2. 구현 순서

```
U0 공통 기반
 └─ U1 인증·사용자 ─┬─ U2 취향 프로파일 ─────────────┐
                    └─ U3 여행·참여자 ─┬─ U4 1:1 초대   │
                                       └─ U6 일정 ──────┴─ U7 AI 일정 생성 ─ U8 통합
                         U5 장소 ───────┘
```

권장 진행 순서: U0 -> U1 -> U2 -> U3 -> U4 -> U5 -> U6 -> U7 -> U8
(U5는 의존이 없어 U3~U4와 병행 가능하다. 팀원과 나누어 진행할 때는 위 그래프의 갈래를 기준으로 나눈다.)

현재 상태: U1의 `users` 엔티티, `UserRepository`, Repository 테스트 3개 완료.

## 3. Unit별 상세

### U0. 공통 기반

모든 API가 쓰는 뼈대를 먼저 만든다.

- 공통 응답 `ApiResponse` (`success`, `data`, `message`(선택), `error`)
- 에러 응답 형식: `error: { code, message, details }`
- `ErrorCode`와 공통 예외 처리 (`@RestControllerAdvice`)
  - 400 검증 실패 / 401 인증 실패 / 403 권한 없음 / 404 없음 / 409 충돌 / 500
- 테스트 환경 정리 (JSONB, pgvector 컬럼과 H2 호환 문제. 5절 참고)

완료 기준: 가짜 컨트롤러로 성공·실패 응답이 명세 형식대로 나오는 테스트 통과.

### U1. 인증·사용자 (테이블 1 / API 5)

테이블: `users` (엔티티·Repository 완료)

| API | 구현 포인트 |
| --- | --- |
| `POST /auth/signup` | 이메일 중복 시 409, 가입 즉시 토큰 발급, 201 |
| `POST /auth/login` | 이메일/비밀번호 검증, 토큰과 `expires_in` 반환 |
| `GET /users/me` | 토큰으로 현재 사용자 조회 |
| `PATCH /users/me` | 닉네임, 프로필 이미지 수정 |
| `GET /users/search` | `query` 최소 2자, `limit` 기본 10, 닉네임·이메일 검색 |

- Step 순서: 비밀번호 저장 방식 결정(5절) -> Spring Security + JWT 필터 -> signup/login -> me -> search
- 가입·로그인을 제외한 모든 요청은 Bearer 토큰이 필요하다. 이 Unit에서 보안 설정을 완성해 이후 Unit이 그대로 쓰게 한다.
- 검색 결과에서 본인은 제외하는지 정한다. (5절)

### U2. 취향 프로파일 (테이블 1 / API 2)

테이블: `user_preference_profiles` (`users`와 1:1, `user_id` UNIQUE)

| API | 구현 포인트 |
| --- | --- |
| `GET /users/me/preference-profile` | 없으면 404, `has_embedding`은 embedding 존재 여부 |
| `PUT /users/me/preference-profile` | UPSERT: 없으면 생성, 있으면 덮어쓰고 `version` +1 |

- `profile_data`는 JSONB, `embedding`은 VECTOR(1536), NULL 허용.
- 퀴즈 문항은 클라이언트 에셋이라 서버는 `answers`만 받는다. 응답 -> 점수(`scores`, `pace`, 태그) 계산 방식은 5절에서 정한다.
- 같은 요청을 두 번 보내도 레코드는 사용자당 1개여야 한다. (테스트 필수)

### U3. 여행·참여자 (테이블 2 / API 7)

테이블: `trips`, `trip_members` (`UNIQUE(trip_id, user_id)`)

| API | 구현 포인트 |
| --- | --- |
| `POST /trips` | 생성자는 `trip_members`에 OWNER로 자동 등록(같은 트랜잭션), `status`는 PLANNING, 201 |
| `GET /trips` | 내가 참여 중인(ACTIVE) 여행만, `my_role`, `member_count` 포함 |
| `GET /trips/{tripId}` | 멤버 목록 포함, 멤버가 아니면 접근 불가, `has_preference`는 취향 프로파일 존재 여부 |
| `PATCH /trips/{tripId}` | OWNER만 가능, 아니면 403 |
| `DELETE /trips/{tripId}` | OWNER만 가능, 삭제 방식은 5절에서 결정 |
| `DELETE /trips/{tripId}/members/me` | MEMBER만 나가기 가능, OWNER는 나갈 수 없음(방식 결정 필요) |
| `DELETE /trips/{tripId}/members/{userId}` | OWNER만 가능, 자기 자신은 불가 |

- 여행당 OWNER는 항상 1명이어야 한다. 서비스 로직과 테스트로 보장한다.
- `end_date`가 `start_date`보다 앞서면 400.
- 참여자 권한 확인(멤버 여부, OWNER 여부)은 이후 Unit도 쓰므로 공통 메서드로 만든다.

### U4. 1:1 초대 (테이블 1 / API 6)

테이블: `trip_invitations`
상태: PENDING / ACCEPTED / REJECTED / CANCELED / EXPIRED

| API | 구현 포인트 |
| --- | --- |
| `POST /trips/{tripId}/invitations` | `invitee_user_id` 또는 `invitee_email` 중 하나는 필수(없으면 400), 이미 대기 중인 초대나 이미 멤버면 409, 201 |
| `GET /trips/{tripId}/invitations` | 보낸 초대 목록과 상태 |
| `DELETE /trips/{tripId}/invitations/{invitationId}` | PENDING만 취소 -> CANCELED |
| `GET /invitations/me` | 내가 받은 PENDING 초대, 만료된 것은 제외 |
| `POST /invitations/{invitationId}/accept` | ACCEPTED로 바꾸고 `trip_members`에 MEMBER로 추가(같은 트랜잭션) |
| `POST /invitations/{invitationId}/reject` | REJECTED로 변경 |

- 수락·거절은 초대받은 본인만 가능하다. 다른 사람이면 403.
- 만료(`expires_at`) 지난 초대를 수락하면 거절 처리한다. 상태 변경 시점은 5절에서 정한다.
- 중복 PENDING 방지(`UNIQUE(trip_id, invitee_user_id) WHERE status='PENDING'`)는 부분 인덱스라 H2에서 안 될 수 있다. 서비스 로직으로도 막는다.

### U5. 장소 (테이블 1 / API 1)

테이블: `places` (`external_place_id` UNIQUE, NULL 허용)

| API | 구현 포인트 |
| --- | --- |
| `GET /places/search` | `keyword` 필수, `latitude`·`longitude`·`radius`(기본 5000m) 선택 |

- MVP는 내부 `places` 테이블 검색만 한다. 외부 지도 API 연동 여부는 5절에서 정한다.
- 일정 생성 결과가 장소를 `places`에 저장하므로, U7에서도 이 엔티티를 사용한다.

### U6. 일정 (테이블 3 / API 7)

테이블: `itineraries`, `itinerary_days`, `itinerary_items`
상태: DRAFT / ACTIVE / ARCHIVED, `UNIQUE(trip_id, version)`

| API | 구현 포인트 |
| --- | --- |
| `GET /trips/{tripId}/itineraries` | 버전 목록 |
| `GET /trips/{tripId}/itineraries/{itineraryId}` | 일차 -> 항목 -> 장소 중첩 구조 조회, `place`는 NULL 가능 |
| `POST .../activate` | 선택한 일정을 ACTIVE로, 기존 ACTIVE는 같은 트랜잭션에서 ARCHIVED로 |
| `PATCH .../itineraries/{itineraryId}` | 일정 이름·상태 수정 |
| `POST .../days/{dayId}/items` | 수동 항목 추가, `place_id`는 NULL 가능, 201 |
| `PATCH /trips/{tripId}/itinerary-items/{itemId}` | 시간, 순서, 비용 수정 |
| `DELETE /trips/{tripId}/itinerary-items/{itemId}` | 항목 삭제 |

- 여행당 ACTIVE 일정은 1개만 유지한다. 서비스 로직과 테스트로 보장한다.
- 항목 수정·삭제 URL에는 `itineraryId`가 없다. `itemId`가 해당 `tripId`의 일정에 속하는지 반드시 확인한다.
- 중첩 조회 시 N+1 쿼리가 생기지 않도록 fetch 방식을 정한다.
- U7 전에는 생성 로직이 없으므로, 테스트에서 Repository로 직접 일정 데이터를 넣어 검증한다.

### U7. AI 일정 생성 (테이블 2 / API 3)

테이블: `trip_member_snapshots`, `generation_jobs`
작업 상태: PENDING / RUNNING / COMPLETED / FAILED

| API | 구현 포인트 |
| --- | --- |
| `POST /trips/{tripId}/generation-jobs` | 참여자 취향을 스냅샷으로 복제하고 작업 등록, 202와 `job_id` 반환 |
| `GET /trips/{tripId}/generation-jobs/{jobId}` | 상태별 응답(RUNNING: 진행률, COMPLETED: `itinerary_id`, FAILED: `error_message`) |
| `GET /trips/{tripId}/generation-jobs` | 작업 이력 목록 |

- Step 순서
  1. `trip_member_snapshots`, `generation_jobs` 엔티티와 Repository
  2. 작업 접수 API (스냅샷 복제 + PENDING 저장 + 202)
  3. 비동기 실행 구조 (요청 스레드와 분리)
  4. 가짜 생성기(Stub)로 일정을 저장해 COMPLETED까지 흐름을 먼저 완성
  5. 실제 AI 연동으로 교체
- 스냅샷은 `profile_snapshot`에 프로파일 복사본을 저장해 이후 취향이 바뀌어도 변하지 않게 한다.
- 취향 프로파일이 없는 참여자가 있을 때의 처리(400 또는 제외)를 정한다. (5절)
- 작업 완료 시 생성된 일정은 `itineraries.generation_job_id`로 연결하고, 상태는 DRAFT로 저장한다.
- 입력 원본은 `input_data`, AI 응답 원본은 `result_data`(JSONB)에 남긴다.

### U8. 통합 마무리

- MVP 5단계 흐름 통합 테스트: 가입 -> 취향 저장 -> 여행 생성 -> 초대·수락 -> 일정 생성 -> 조회 -> 확정
- 모든 API의 인증·권한(401, 403, 404) 점검
- 비밀값 분리 확인, 팀원이 실행할 수 있는 실행 방법 문서화

## 4. Unit 완료 기준 (Definition of Done)

- 해당 Unit의 테이블 엔티티와 Repository가 있고, Repository 테스트(저장·조회·제약 위반)가 통과한다.
- 해당 Unit의 모든 API가 명세의 상태 코드와 응답 형식(`ApiResponse`)을 따른다.
- 정상 응답뿐 아니라 실패 케이스(400, 401, 403, 404, 409 중 해당하는 것)를 테스트한다.
- 엔티티를 응답으로 직접 반환하지 않고 DTO를 사용한다.
- `./gradlew test` 전체가 통과한다.
- 커밋 메시지와 브랜치가 `../CLAUDE.md`의 Git 규칙을 따른다.

## 5. 결정이 필요한 사항 (구현 전 팀과 합의)

| # | 내용 | 왜 필요한가 | 제안 |
| --- | --- | --- | --- |
| 1 | `users`에 비밀번호 컬럼이 없다 | API는 `password`로 가입·로그인하는데 모델링 문서의 `users`에는 저장할 컬럼이 없다 | `password_hash` 컬럼 추가(BCrypt)를 팀에 제안하고 모델링 문서를 함께 수정. 구글 로그인 등 외부 인증으로 바꾼다면 API 명세를 수정 |
| 2 | H2와 PostgreSQL 전용 타입 | JSONB, VECTOR(1536), 부분 유니크 인덱스는 H2에서 안 될 수 있다 | 해당 컬럼이 있는 테이블(U2, U7 등)에서 테스트 방식을 정한다. 예: H2의 PostgreSQL 모드, 실제 PostgreSQL 컨테이너 |
| 3 | 스키마 생성 방식 | 부분 유니크 인덱스, pgvector 확장은 JPA 어노테이션만으로 만들기 어렵다 | ddl-auto로 시작하되 별도 스키마 SQL을 둘지 결정 |
| 4 | 취향 점수 계산 방식 | `PUT preference-profile`이 "AI 분석/계산"으로 명시돼 있다 | MVP는 규칙 기반으로 점수를 계산하고 `embedding`은 NULL, 이후 AI 연동으로 교체 |
| 5 | AI 일정 생성 연동 방식 | 모델, 호출 방식, 비동기 실행 방식이 정해지지 않았다 | U7에서 Stub으로 흐름을 먼저 완성하고 실제 연동은 별도 이슈로 분리 |
| 6 | 장소 검색 범위 | 명세는 "내부 등록 장소 및 지도 POI" | MVP는 내부 `places`만 검색, 외부 지도 연동은 이후 |
| 7 | 여행 삭제 방식 | 모델링에 삭제 표시 컬럼이 없다 | 실제 삭제할지, `trips.status`로 구분할지 결정 |
| 8 | OWNER가 여행을 나가는 경우 | OWNER는 1명 유지 규칙이 있다 | MVP는 OWNER는 나갈 수 없고 여행 삭제만 가능 |
| 9 | 나갔던 멤버의 재참여 | `UNIQUE(trip_id, user_id)`와 `status=LEFT` | 새 행을 만들지 않고 기존 행을 ACTIVE로 되돌림 |
| 10 | 초대 만료 처리 | EXPIRED 상태가 있고 `expires_at`이 있다 | 조회·수락 시점에 만료를 판정해 상태 변경(스케줄러는 이후) |
| 11 | 상세 명세가 없는 API | 아래 API는 목록에만 있고 요청·응답 예시가 없다 | 구현 전에 형식을 팀과 맞춘다 |

상세 명세가 없는 API: `PATCH /users/me`, `PATCH /trips/{tripId}`, `DELETE /trips/{tripId}`, `DELETE /trips/{tripId}/members/me`, `DELETE /trips/{tripId}/members/{userId}`, `GET /trips/{tripId}/invitations`, `DELETE .../invitations/{invitationId}`, `GET /trips/{tripId}/generation-jobs`, `PATCH /trips/{tripId}/itineraries/{itineraryId}`

위 항목은 제안이며 확정된 팀 결정이 아니다. 합의되면 이 표를 갱신한다.

## 6. 진행 체크리스트

- [ ] U0 공통 기반
- [ ] U1 인증·사용자 — [x] `users` 엔티티·Repository·테스트 완료 (PR #2) / [ ] API 5개
- [ ] U2 취향 프로파일 — [ ] 엔티티 [ ] API 2개
- [ ] U3 여행·참여자 — [ ] 엔티티 2개 [ ] API 7개
- [ ] U4 1:1 초대 — [ ] 엔티티 [ ] API 6개
- [ ] U5 장소 — [x] `places` 엔티티·Repository·테스트 완료 (PR #8) / [ ] API 1개
- [ ] U6 일정 — 엔티티 3개 중 2개 완료 / [ ] API 7개
  - [x] `itineraries` 엔티티·Repository·테스트 완료 (PR #10)
  - [x] `itinerary_days` 엔티티·Repository·테스트 완료 (feature/12, PR 머지 대기)
  - [ ] `itinerary_items` 엔티티·Repository·테스트
- [ ] U7 AI 일정 생성 — [ ] 엔티티 2개 [ ] API 3개 [ ] Stub 생성기 [ ] 실제 AI 연동
- [ ] U8 통합 마무리
