# Travel Maker Backend

AI 그룹 여행 일정 서비스 **Travel Maker** 백엔드 레포지토리

## 기술 스택

- **Language / Runtime**: Java 17
- **Framework**: Spring Boot 4.1.2 (WebMVC, Data JPA, Security, Validation)
- **Build**: Gradle
- **Database**: PostgreSQL 16 (`pgvector`), Test DB: H2
- **Auth**: Firebase Admin SDK

## 로컬 개발 환경 실행

### 1. 환경변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열어 `DB_PASSWORD`, `FIREBASE_PROJECT_ID` 등 로컬 환경에 맞게 값을 수정합니다.

### 2. PostgreSQL 실행 (Docker)

```bash
docker compose up -d
```

- 이미지: `pgvector/pgvector:pg16`
- 상태 확인: `docker compose ps`
- 중지: `docker compose down`
- 데이터 볼륨 포함 초기화: `docker compose down -v`

### 3. 애플리케이션 실행

`local` 프로필을 활성화해 실행합니다. (`application-local.properties`가 프로젝트 루트의 `.env`를 자동으로 불러옵니다.)

**터미널**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

**IntelliJ IDEA**
1. 실행 구성(`TravelMakerApplication`) → **Edit Configurations...**
2. **Active profiles**에 `local` 입력 후 실행

## 테스트 실행

```bash
./gradlew test
```

## 프로젝트 문서

- [AGENTS.md](./AGENTS.md) ([CLAUDE.md](./CLAUDE.md)): 프로젝트 목표, MVP 범위, Git 협업 및 코딩/테스트 규칙
- [docs/PRD.md](./docs/PRD.md): 구현 단위 및 순서
