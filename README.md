# Marketflow

Spring Boot 기반의 커머스 API 프로젝트입니다. 현재는 상품/상품 옵션 엔티티, 더미 데이터 초기화, 상품 목록/상세 조회 API, 장바구니 기반 주문 생성, 결제 시도 이력과 상태 전이 모델을 포함합니다.

재고 차감은 DB 조건부 UPDATE로 재고 확인과 차감을 한 번에 처리합니다. 관련 동시성 테스트와 해석은 [Atomic UPDATE 재고 차감 문서](docs/stock-concurrency-atomic-update.md), 전략 선택 근거는 [재고 차감 전략 비교 문서](docs/stock-concurrency-comparison.md)를 참고합니다.

## 기술 스택

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- MySQL 8
- Gradle
- Lombok

## 설정 파일 구조

| 파일 | Git 포함 | 용도 |
| --- | --- | --- |
| `src/main/resources/application.yml` | 포함 | 공통 설정, 기본 프로필 `local` 지정 |
| `src/main/resources/application-local.yml` | 제외 | 로컬 개발용 실제 DB 설정 |
| `src/main/resources/application-local.sample.yml` | 포함 | 로컬 개발용 설정 샘플 |
| `src/main/resources/application-prod.yml` | 포함 | 배포용 설정, 환경변수 기반 |
| `src/test/resources/application-test.yml` | 포함 | 테스트용 MySQL DB 설정 |
| `.env` | 제외 | 배포/실행 환경의 실제 환경변수 |
| `.env.sample` | 포함 | 필요한 환경변수 샘플 |

`application-local.yml`과 `.env`에는 실제 접속 정보가 들어갈 수 있으므로 Git에 커밋하지 않습니다.

## 로컬 실행

### 1. 로컬 설정 파일 생성

```bash
cp src/main/resources/application-local.sample.yml src/main/resources/application-local.yml
```

샘플 설정은 `docker-compose.yml`의 MySQL 기본값과 맞춰져 있습니다.

### 2. MySQL 실행

```bash
docker compose up -d mysql
```

기본 DB 정보:

```text
database: marketflow
test database: marketflow_test
username: marketflow
password: marketflow
port: 3306
```

`docker-compose.yml`은 MySQL 컨테이너 최초 초기화 시 `marketflow_test`도 함께 생성합니다. 이미 기존 볼륨이 있는 상태라면 초기화 SQL이 다시 실행되지 않으므로, 테스트 DB가 없을 때는 아래 명령으로 직접 생성합니다.

```bash
docker compose exec mysql mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS marketflow_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON marketflow_test.* TO 'marketflow'@'%'; FLUSH PRIVILEGES;"
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 프로필은 `local`입니다. 별도로 지정하려면 아래처럼 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### 4. API 확인

```bash
curl 'http://localhost:8080/api/products'
curl 'http://localhost:8080/api/products/1'
```

상품 데이터가 없으면 애플리케이션 시작 시 더미 상품과 옵션이 자동으로 추가됩니다.

## 테스트

테스트는 `test` 프로필과 테스트 전용 DB `marketflow_test`를 사용합니다. 로컬 개발 DB `marketflow`와 분리되어 있으므로 테스트의 `save()` / `deleteAll()`은 `marketflow_test`에만 적용됩니다.

로컬 MySQL이 실행 중인 상태에서 테스트를 실행합니다.

```bash
./gradlew test
```

테스트 설정은 기본적으로 다음 DB를 바라봅니다.

```text
jdbc:mysql://localhost:3306/marketflow_test
```

## 배포 설정

배포 환경에서는 `prod` 프로필을 사용합니다.

```bash
cp .env.sample .env
```

`.env`에 실제 값을 입력합니다.

```env
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://your-db-host:3306/marketflow?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
JPA_DDL_AUTO=validate
```

실행 환경이 위 값을 환경변수로 로드하면 `application-prod.yml`의 `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`에 주입됩니다.

직접 실행 예시:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL='jdbc:mysql://your-db-host:3306/marketflow?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export DB_USERNAME='your_db_username'
export DB_PASSWORD='your_db_password'
export JPA_DDL_AUTO=validate

./gradlew bootRun
```

## CI 테스트 기준

CI에서는 운영 MySQL 서버를 사용하지 않습니다. GitHub Actions 같은 배포 파이프라인에서는 CI 전용 MySQL service/container를 띄우고 그 안의 `marketflow_test` DB로 테스트합니다.

테스트 실행 프로필은 `test`입니다.

```bash
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

`prod` 프로필은 실제 애플리케이션 배포 실행용이며, 테스트 실행에는 사용하지 않습니다.
