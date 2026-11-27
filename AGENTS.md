# Travel Maker 백엔드 (CLAUDE.md)

이 파일은 개발 중 항상 지켜야 할 목표, MVP 범위, 규칙을 담는다. 작업 전에 먼저 읽는다.

## 1. 프로젝트 목표

- 서비스: AI 그룹 여행 일정 서비스 (팀: GDGOC 하이파이브)
- 해결할 문제: 여럿이 떠나는 여행은 가고 싶은 곳과 선호가 제각각이라 일정을 맞추기 어렵다.
- 목표: 멤버 각자의 취향을 모아, AI가 모두에게 맞는 여행 일정을 만들어 준다.
- 핵심 축 3가지
  1. 취향 프로파일: 퀴즈 응답으로 사용자별 취향을 만든다.
  2. 그룹 여행: 여행을 만들고 친구를 초대한다.
  3. AI 일정 생성: 참여자 모두의 취향을 반영해 일정을 만든다.

## 2. MVP 범위

> 아래 범위는 API 명세(6개 도메인, 31개 API)와 서비스 흐름 5단계를 기준으로 정리했다. 팀에서 따로 정한 MVP 문서가 있으면 그 문서를 우선한다.

MVP는 아래 5단계 흐름이 처음부터 끝까지 한 번 동작하는 것이다.

| 단계 | 기능 | API 도메인 |
| --- | --- | --- |
| 1 | 가입·로그인, 내 정보 | auth/users (5개) |
| 2 | 취향 퀴즈 응답으로 취향 프로파일 저장 | preference (2개) |
| 3 | 여행 생성, 멤버와 역할(OWNER/MEMBER) 관리 | trips/members (7개) |
| 4 | 닉네임·이메일로 1:1 친구 초대, 수락·거절 | invitations (6개) |
| 5 | AI 일정 생성 요청(202 비동기), 상태 조회, 일정·장소 조회, 일정 확정 | AI generation jobs (3개), itineraries/places (8개) |

- 완료 기준: 위 5단계를 API로 끝까지 호출해 결과를 확인할 수 있다.
- 스펙에 없는 기능은 MVP 범위 밖이다. 추가하려면 먼저 팀과 합의한다.

## 3. 작업 방식

- 힌트를 받고 직접 구현한다. 완성 코드는 요청할 때만 제공한다.
- 내가 맡은 범위만 구현한다. 다른 팀원 영역은 건드리지 않는다.
- 작업 전에 `git pull`로 최신 상태를 받는다.

## 4. Git 협업

- 순서: Issue -> Branch -> Commit -> Push -> PR
- 이슈를 먼저 만들고 그 번호로 브랜치를 만든다. (`git switch -c feature/<이슈번호>`)
- main에 직접 커밋하지 않는다.
- 커밋 메시지: `<type>: <subject>`
  - type: feat / fix / docs / style / refactor / test / chore / design / perf
  - 이슈 번호는 `Resolves: #번호`로 남긴다.
- 커밋은 기능 단위로 쪼갠다. (예: 엔티티 / 레포지토리 / 테스트)
- 커밋 전에 `git status`로 스테이징을 확인한다. 불필요한 파일은 `git restore --staged .`로 비운다.
- 처음 올릴 때 `git push -u origin feature/<번호>`, 이후에는 `git push`
- 테스트 통과 후에 커밋, 푸시, PR 순서로 진행한다.

## 5. 기술 환경

- Java 17, Spring Boot 4.1.2, Gradle
- 운영 DB는 PostgreSQL, 테스트 DB는 H2. MySQL은 쓰지 않는다.
- 패키지: `com.travelmaker.backend` 아래 `entity`, `repository`

## 6. DB 모델링 규칙 (Notion 모델링 문서 기준)

- 컬럼명은 문서의 snake_case와 똑같이 맞춘다.
- PK는 UUID, 시각은 TIMESTAMPTZ, 취향과 AI 데이터는 JSONB로 저장한다.
- 여행당 OWNER는 1명, 여행당 ACTIVE 일정은 1개로 유지한다.
- 일정을 만들 때의 취향은 스냅샷으로 복제해 고정한다.
- 11개 테이블
  - 사용자·여행: users, trips, trip_members, trip_invitations
  - 취향·스냅샷: user_preference_profiles, trip_member_snapshots
  - 장소·일정·AI: places, itineraries, itinerary_days, itinerary_items, generation_jobs

## 7. 엔티티 규칙

- 테이블 하나당 엔티티 하나를 만든다.
- `@Entity`와 `@Table(name = "...")`을 함께 쓴다. 예약어는 피한다. (user -> users)
- PK는 `@Id` + `@GeneratedValue(strategy = GenerationType.UUID)`
- 시각은 `Instant`로 쓴다. `OffsetDateTime`은 JPA Auditing이 지원하지 않아 쓰지 않는다.
- 생성·수정 시각은 `@CreatedDate` / `@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)`
  - `@EnableJpaAuditing`은 `TravelMakerApplication`에 한 번만 둔다.
- `@NoArgsConstructor(access = PROTECTED)`, `@Getter`를 쓰고 `@Setter`는 쓰지 않는다.
- 생성자는 id와 시각을 받지 않는다. 이 값은 JPA가 채운다.
- 제약은 `@Column(nullable, unique, length)`로 명시한다.
- 엔티티를 API 응답으로 그대로 반환하지 않는다. DTO로 분리한다.
- Repository는 interface로 만들고 `JpaRepository<엔티티, UUID>`를 상속한다.

## 8. 테스트 규칙

- 테이블마다 Repository 테스트를 만든다. (저장, 조회, 제약 위반)
- UNIQUE 같은 제약 위반은 `save`가 아니라 `saveAndFlush`로 검증한다.
- Boot 4의 `@DataJpaTest` 경로: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
- `assertThat` 계열은 static import 한다.
- 주의: JSONB, pgvector 컬럼은 PostgreSQL 전용이라 H2에서 오류가 날 수 있다. 해당 테이블을 구현할 때 테스트 방식을 따로 정한다.

## 9. API 규약

- 기본 주소: `/api/v1`
- 응답 형식: `{ success, data, error }`
- 상태 코드: 201 생성 / 202 비동기 접수 / 409 충돌 / 400 검증 실패
- 가입·로그인을 제외한 모든 API는 `Authorization: Bearer <JWT>`로 인증한다.
- AI 일정 생성은 202로 접수하고, 상태는 폴링으로 확인한다.

## 10. 보안

- JWT 비밀키, DB 비밀번호 같은 비밀값은 커밋하지 않는다. 환경변수나 `.env`로 분리한다.

## 11. 진행 현황

- 완료: DB 모델링, API 명세, 프로젝트 초기 세팅, users 엔티티 + UserRepository + 테스트 3개 통과
- 다음: 나머지 10개 테이블의 엔티티·Repository -> Service·Controller로 API 31개 -> JWT 인증, 공통 예외 처리 -> AI 일정 생성 연동, 통합 테스트
