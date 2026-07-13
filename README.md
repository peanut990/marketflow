# Marketflow

Spring Boot 기반의 커머스 API 프로젝트입니다. 현재는 상품/상품 옵션 엔티티, 더미 데이터 초기화, 상품 목록/상세 조회 API를 포함합니다.

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
username: marketflow
password: marketflow
port: 3306
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

로컬 MySQL이 실행 중인 상태에서 테스트를 실행합니다.

```bash
./gradlew test
```

현재 테스트는 `local` 기본 프로필과 MySQL 설정을 사용합니다.

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
