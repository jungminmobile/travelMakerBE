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

## Firebase Auth + Supabase + Google Cloud Run 배포

운영 환경에서는 `prod` 프로필([application-prod.properties](./src/main/resources/application-prod.properties))과 [Dockerfile](./Dockerfile)을 사용합니다.

### 1. 필수 환경변수

| 변수명 | 설명 | 비고 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Spring 프로필 | `Dockerfile` 기본값 `prod` |
| `DB_URL` | PostgreSQL JDBC URL | 아래 Supabase 연결 안내 참고 |
| `DB_USERNAME` | DB 사용자명 | Supabase Pooler 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 | Secret Manager 등 안전하게 관리 |
| `FIREBASE_PROJECT_ID` | Firebase 프로젝트 ID | 예: `gen-lang-client-0144634141` |
| `FRONTEND_ORIGIN` | 허용할 프론트엔드 Origin | 쉼표(`,`)로 다중 Origin 지정 가능 |
| `JPA_DDL_AUTO` | Hibernate DDL 모드 | 기본값 `update` |
| `AI_GENERATOR` | 일정 생성기 구현체 | `ai` (기본값) 또는 `stub` |
| `AI_SERVER_BASE_URL` | AI 서버 기본 URL | 운영 AI 서버 주소 지정 |

### 2. Supabase PostgreSQL 연결 권장 사항

- Cloud Run은 기본적으로 IPv4 아웃바운드를 사용하므로, IPv6 전용인 Supabase Direct Connection 대신 **Session Pooler**(`5432` 포트, IPv4 호환) 연결 문자열을 사용하는 것을 권장합니다.
  - 예: `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`
- 만약 **Transaction Pooler**(`6543` 포트)를 사용해야 한다면, JDBC PreparedStatement 캐시 충돌을 방지하기 위해 `&prepareThreshold=0` 옵션을 추가하세요.
- 연결 풀 고갈 방지를 위해 `application-prod.properties`의 기본 HikariCP 최대 풀 크기는 `5`(`DB_POOL_MAX_SIZE`)로 제한되어 있습니다.

### 3. Firebase Auth 인증 (ADC)

- `FirebaseConfig`는 `GoogleCredentials.getApplicationDefault()`(ADC)를 사용합니다.
- **Cloud Run**: 서비스에 연결된 런타임 서비스 계정의 ADC가 자동으로 사용되므로 서비스 계정 JSON 키 파일을 컨테이너에 포함할 필요가 없습니다.
- **로컬**: `gcloud auth application-default login`을 실행하거나 `GOOGLE_APPLICATION_CREDENTIALS` 환경변수에 서비스 계정 키 경로를 지정합니다.

### 4. `release` 브랜치 자동 배포 (GitHub Actions)

[deploy-cloud-run.yml](./.github/workflows/deploy-cloud-run.yml)은 `release` 브랜치에 푸시(또는 GitHub Actions 수동 `workflow_dispatch`) 시 다음 순서로 Cloud Run에 자동 배포합니다.

1. Java 17 환경에서 `./gradlew test` 실행
2. Google Cloud Workload Identity Federation(OIDC) 인증 후 Artifact Registry에 Docker 이미지 빌드·푸시
3. Cloud Run 서비스(`travel-maker-backend`, `asia-northeast3`) 배포

#### 사전 준비 (GCP 결제 프로필 연결 후 1회 설정)

- **GCP API 활성화**: Cloud Run Admin(`run.googleapis.com`), Artifact Registry(`artifactregistry.googleapis.com`), Secret Manager(`secretmanager.googleapis.com`), IAM Credentials(`iamcredentials.googleapis.com`)
- **Artifact Registry 저장소**: `asia-northeast3` 리전에 Docker 저장소 `travel-maker` 생성
- **Secret Manager**: Supabase DB 비밀번호를 시크릿(기본 이름 `travel-maker-db-password`)으로 등록
- **Workload Identity Federation & IAM**:
  - `jungminmobile/travelMakerBE`의 `release` 브랜치를 허용하는 Workload Identity Pool/Provider 생성 및 배포용 서비스 계정에 연결 (`attribute.repository == 'jungminmobile/travelMakerBE'` 및 `refs/heads/release` 조건 권장)
  - 배포용 서비스 계정 권한: `roles/run.admin`, `roles/artifactregistry.writer`, `roles/iam.serviceAccountUser`
  - Cloud Run 런타임 서비스 계정 권한: `roles/secretmanager.secretAccessor`, `roles/firebaseauth.viewer`
- **Firebase Auth**: Firebase Console → Authentication → Settings → Authorized domains에 운영 프론트엔드 도메인 등록

#### GitHub Repository `Secrets and variables → Actions` 설정

Repository **Settings → Secrets and variables → Actions**에 아래 값을 등록합니다.

| 구분 | 이름 | 설명 |
| --- | --- | --- |
| Secret | `GCP_WORKLOAD_IDENTITY_PROVIDER` | Workload Identity Provider 전체 리소스 경로 |
| Secret | `GCP_SERVICE_ACCOUNT` | 배포용 GCP 서비스 계정 이메일 |
| Secret | `DB_URL` | Supabase Session Pooler JDBC URL |
| Secret | `DB_USERNAME` | Supabase Pooler 사용자명 |
| Variable | `FRONTEND_ORIGIN` | 운영 프론트엔드 Origin (다중 지정 가능) |
| Variable (선택) | `GCP_PROJECT_ID`, `GCP_REGION`, `CLOUD_RUN_SERVICE`, `GAR_REPOSITORY` | 기본값(`gen-lang-client-0144634141`, `asia-northeast3`, `travel-maker-backend`, `travel-maker`) 재정의 시 |
| Variable (선택) | `FIREBASE_PROJECT_ID`, `DB_PASSWORD_SECRET`, `JPA_DDL_AUTO`, `AI_GENERATOR`, `AI_SERVER_BASE_URL` | 기본값 재정의 시 |

## 프로젝트 문서

- [AGENTS.md](./AGENTS.md) ([CLAUDE.md](./CLAUDE.md)): 프로젝트 목표, MVP 범위, Git 협업 및 코딩/테스트 규칙
- [docs/PRD.md](./docs/PRD.md): 구현 단위 및 순서
