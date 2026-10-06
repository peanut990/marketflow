# 주문·결제 성공과 실패 시나리오

현재 구현된 주문 생성, 결제 준비·조회, 일반 주문 취소를 대상으로 설명한다. **결제 준비 성공은 카드 결제 성공이 아니다.** PG 승인·환불 API는 아직 없으며, 이 문서의 SQL은 현재 코드의 동작을 설명하기 위한 축약 예시다.

## 1. 전체 흐름과 읽는 순서

```text
장바구니
  -> 주문 생성: 재고 차감, Order.CREATED, 주문상품 저장, 장바구니 삭제
  -> 결제 준비: Payment.READY 생성 또는 기존 시도 재사용
  -> 결제 조회: 저장된 상태만 확인

결제를 진행하지 않고 주문 취소
  -> Order.CANCELED + READY를 EXPIRED로 변경 + 재고 복구

PG 승인 -> 실제 결제 완료 -> PG 전액 취소
  위 흐름은 후속 구현이며 현재 HTTP API로 수행할 수 없다.
```

| 요청 | 서비스 | 성공 응답 | 재고 변화 |
| --- | --- | --- | --- |
| `POST /api/orders` | `OrderService.createOrder()` | 201 | 주문 수량만큼 감소 |
| `POST /api/orders/{orderId}/payments` | `PaymentService.prepare()` | 신규 201, 재사용 200, 승인 진행 중 202 | 없음 |
| `GET /api/payments/{paymentId}` | `PaymentService.getPayment()` | 200 | 없음 |
| `POST /api/orders/{orderId}/cancel` | `OrderService.cancelOrder()` | 200 | 취소한 주문 수량만큼 증가 |

Payment에는 IN_PROGRESS, DONE, FAILED, CANCELED 상태와 전이 메서드가 있지만, 아직 PG 응답으로 이 상태를 만드는 API는 없다. 관련 테스트는 엔티티 메서드로 상태를 미리 구성한다. 현재 API가 직접 수행하는 전이는 **새 READY 생성**, **주문 취소에 따른 READY -> EXPIRED**다.

## 2. 먼저 구분할 개념

### 트랜잭션, flush, commit

일반적인 HTTP 요청에서는 컨트롤러가 트랜잭션 프록시를 통해 서비스를 호출한다.

```text
Controller
  -> 트랜잭션 시작
  -> Service 실행: 조회, 검증, 엔티티 변경, SQL 실행
  -> 남은 변경 flush + commit
  -> Controller가 성공 응답 반환

서비스 실행/flush 중 런타임 예외
  -> rollback
  -> 예외 처리기가 오류 응답 반환
```

| 용어 | 이 프로젝트에서의 의미 |
| --- | --- |
| 영속성 컨텍스트 | 트랜잭션에서 조회한 엔티티와 변경을 관리하는 JPA 영역 |
| Dirty checking | `order.cancel()`처럼 엔티티를 바꾸면 flush 시 변경을 SQL로 반영하는 방식 |
| flush | 보류된 변경을 SQL로 DB에 보내는 것. 트랜잭션은 아직 끝나지 않음 |
| commit | 해당 트랜잭션의 변경을 최종 확정 |
| rollback | 해당 트랜잭션에서 실행한 DB 변경을 취소 |

**SQL을 실행했다는 것과 커밋했다는 것은 다르다.** 첫 번째 재고 UPDATE가 성공했어도 뒤의 작업이 실패하면 함께 롤백한다. 현재 서비스에서 던지는 비즈니스 예외는 런타임 예외이므로 기본 롤백 대상이다. [Spring 트랜잭션 롤백 규칙](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)

`@Transactional` 기본 전파 방식은 REQUIRED이므로 상위 트랜잭션이 있으면 참여한다. 따라서 위 경계는 일반 HTTP 호출 기준이다. `@Transactional` 테스트에서는 서비스가 테스트 트랜잭션에 참여하고 테스트 종료 시 롤백될 수 있다.

### 두 종류의 락

| 보호할 대상 | 현재 방식 | 목적 |
| --- | --- | --- |
| 같은 주문의 결제 준비·취소 | 주문을 `SELECT ... FOR UPDATE`로 조회 | 결제 중복 생성, 준비·취소 간 상태 경쟁 방지 |
| 같은 옵션의 재고 | `UPDATE stock_quantity = stock_quantity +/- 수량`이 획득하는 쓰기 락 | 동시 차감·복구의 갱신 유실 방지 |

**Atomic UPDATE도 락을 사용한다.** Java에서 락 조회 후 계산하는 대신, DB가 UPDATE를 실행하며 해당 재고 행의 경합을 조정하는 것이다. 주문 락과 재고 락은 대상과 목적이 다르며 함께 사용한다.

주문에 잡힌 락이 연관 Payment 전체를 자동으로 잠그는 것은 아니다. 현재 결제 이력 조회에는 별도 `FOR UPDATE`가 없다. 같은 주문을 변경하는 작업이 먼저 동일한 주문 락을 획득한다는 규칙으로 순서를 맞춘다.

획득한 InnoDB 트랜잭션 락은 commit/rollback 시 해제된다. 락 조회 메서드나 UPDATE 메서드가 반환했다고 해제되지 않는다. 보통의 비잠금 SELECT까지 모두 대기시키는 것도 아니다. 여기서는 주요 행 락 중심으로 설명하며 실제 인덱스·FK 검사 등에 따른 추가 락은 생략한다. [MySQL 잠금 읽기](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html), [커밋·롤백과 락 해제](https://dev.mysql.com/doc/refman/8.0/en/innodb-autocommit-commit-rollback.html)

## 3. 주문 생성

### 성공: 재고 10개에서 3개 주문

한 트랜잭션 안에서 다음 순서로 처리한다.

1. 사용자와 소유한 장바구니 항목을 조회한다. 장바구니의 상품 옵션·상품은 EntityGraph로 함께 조회한다.
2. 비활성 옵션과 조회 시점의 재고 부족을 검사하고 총액을 계산한다.
3. 옵션 ID 오름차순으로 조건부 재고 UPDATE를 실행한다.
4. 주문 번호를 생성·중복 조회한 뒤 Order와 OrderItem을 저장한다.
5. 선택한 장바구니 항목을 삭제하고 커밋한다.

핵심 쓰기 SQL은 아래와 같다. 선행 조회, 감사 필드, 생성 ID 등은 생략했다.

```sql
-- 트랜잭션 시작
UPDATE product_options
SET stock_quantity = stock_quantity - 3
WHERE id = :optionId
  AND active = true
  AND stock_quantity >= 3;
-- affected rows = 1, 재고 10 -> 7. 이때부터 쓰기 락 유지

INSERT INTO orders (...) VALUES (...);       -- CREATED
INSERT INTO order_items (...) VALUES (...);  -- 주문 당시 상품명·가격·수량 저장
DELETE FROM cart_items WHERE id = :cartItemId;
-- COMMIT: 재고 7, 주문/주문상품 존재, 장바구니 항목 삭제 확정
```

옵션이 두 개라면 재고 UPDATE도 두 번이다. **현재 구현은 여러 옵션을 하나의 UPDATE로 묶는 방식이 아니다.** 각 UPDATE가 원자적이고, 여러 UPDATE와 주문 저장 전체는 하나의 트랜잭션으로 묶여 있다.

### 실패: 조회할 때는 있었지만 차감할 때는 재고가 없음

재고가 1개인데 A와 B가 각각 1개 주문한다고 가정한다.

| 순서 | 요청 A | 요청 B |
| --- | --- | --- |
| 1 | 조회에서 재고 1 확인 | 조회에서 재고 1 확인 |
| 2 | 조건부 UPDATE 성공, 미커밋 재고 0 | 같은 옵션 UPDATE에서 락 대기 |
| 3 | 주문 저장 후 커밋 | 대기 종료 후 조건 평가, 재고 부족으로 affected rows 0 |
| 4 | 201 | `InsufficientStockException`, 롤백, 400 |

Java의 사전 검증만으로는 이 경쟁을 막을 수 없다. 마지막 방어선은 UPDATE의 `stock_quantity >= 수량` 조건이다. A가 커밋 대신 롤백하면 B는 복원된 재고를 기준으로 차감할 수 있다.

### 실패: 여러 옵션 중 두 번째 차감 실패

```text
옵션 10: UPDATE 성공, 10 -> 7
옵션 20: UPDATE 결과 0건
예외 발생 -> 트랜잭션 전체 rollback

최종 결과: 옵션 10도 재고 10으로 복원
           주문/주문상품 없음, 장바구니 유지
```

현재 코드에서는 모든 재고 차감이 끝나기 전 주문을 저장하지 않는다. 따라서 차감 단계에서 실패하면 이번 시도의 Order/OrderItem INSERT는 아직 없다. 반대로 주문 저장 이후 오류가 나면 이미 실행한 차감과 INSERT도 함께 롤백한다. IDENTITY ID는 INSERT 시 확보될 수 있지만 ID 발급이 커밋을 의미하지는 않는다.

| 실패 지점 | 응답 | DB 결과 |
| --- | --- | --- |
| 사용자 또는 선택한 소유 장바구니 항목 없음 | 404 NOT_FOUND | 변경 없음 |
| 빈 목록·null 장바구니 ID 등 | 400 BAD_REQUEST | 변경 없음 |
| 사전 검증에서 비활성 옵션 | 400 INACTIVE_PRODUCT_OPTION | 차감 전 거절 |
| 사전 검증에서 재고 부족 / 조건부 UPDATE 0건 | 400 INSUFFICIENT_STOCK | 앞선 차감까지 롤백 |
| 주문·주문상품 저장/장바구니 삭제 중 DB 오류 | 별도 결제용 503 매핑 없음 | 일반적인 실행 오류는 트랜잭션 롤백 |

조건부 UPDATE의 0건은 재고 부족뿐 아니라 비활성화 등도 원인일 수 있지만, 현재 서비스는 이를 `INSUFFICIENT_STOCK`으로 통일한다. 조회 이후 재고가 복구됐더라도 사전 재고 검사에서 먼저 탈락할 수 있다. 현재 자동 재시도는 없다.

## 4. 결제 준비

### 성공: 첫 결제 시도 생성

전제는 `Order.CREATED`, 저장된 주문 금액 3,000원, 기존 활성 결제 없음이다.

```sql
-- 트랜잭션 시작
SELECT * FROM orders
WHERE id = :orderId AND user_id = :userId
FOR UPDATE;
-- 주문 행 락 획득 후 주문 상태 검사

SELECT * FROM payments
WHERE order_id = :orderId
ORDER BY created_at ASC;

-- 이력과 금액 검증 후, 서버 UUID와 Order.totalAmount로 생성
INSERT INTO payments (...) VALUES (...); -- READY, amount = 3000
-- COMMIT: 주문 락 해제
```

커밋을 마치면 201과 `paymentId`, `pgOrderId`, `amount`, `status`, `statusUrl`을 반환한다. Location도 `/api/payments/{paymentId}`다. 상품의 현재 가격을 다시 읽거나, 재고를 다시 차감하거나, PG에 승인 요청을 보내지 않는다.

### 성공: 재사용 또는 다시 시도

다음 경우에도 **주문 락 -> 결제 이력 조회** 순서는 동일하다.

| 기존 상태 | 처리 | 응답 | 쓰기 SQL |
| --- | --- | --- | --- |
| READY 1건, 주문 금액 일치 | 동일 Payment 재사용 | 200 | 없음 |
| IN_PROGRESS 1건, 주문 금액 일치 | 기존 진행 중 시도 반환 | 202 | 없음 |
| FAILED/EXPIRED 이력만 있음 | 이력 보존, 새 UUID로 READY 생성 | 201 | 새 Payment INSERT |

202는 이 API가 새로운 비동기 승인 작업을 시작했다는 의미가 아니다. **이미 IN_PROGRESS로 저장된 시도를 반환했다는 의미**다. 현재 준비 API만 호출해서 READY를 IN_PROGRESS로 만들지는 않는다.

### 성공: 같은 주문에 동시에 준비 요청

| 순서 | 요청 A | 요청 B |
| --- | --- | --- |
| 1 | 주문 락 획득 | 같은 주문 락 대기 |
| 2 | 활성 결제 없음 확인, READY 생성 | 아직 결제 이력 조회 전 |
| 3 | 커밋, 락 해제, 201 | 주문 락 획득 |
| 4 | 완료 | A가 만든 READY 조회, 동일 ID로 200 |

서로 다른 UUID를 만드는 것만으로 중복 생성을 막을 수는 없다. `pg_order_id` UNIQUE는 UUID 중복만 막고, 한 주문에 서로 다른 UUID의 READY 두 건이 생기는 것은 막지 않는다. **중복 준비 방지는 주문 락과 락 이후 이력 조회의 조합**이 담당한다.

만약 A의 INSERT가 롤백됐다면 B는 기존 READY를 발견하지 못하고 직접 새 시도를 생성한다. 락을 먼저 얻은 요청이 항상 최종 성공자가 되는 것은 아니다.

### 실패: 검증 거절

아래는 현재 코드의 검사 순서다. 예를 들어 주문 자체가 CANCELED이면 이후 결제 이력 검사는 하지 않는다.

| 순서 | 조건 | 응답 |
| --- | --- | --- |
| 1 | X-USER-ID 헤더 누락 또는 헤더·경로 ID가 양의 Long이 아님 | 400 BAD_REQUEST, 서비스 진입 전 거절 |
| 2 | 소유한 주문이 조회되지 않음 | 404 NOT_FOUND |
| 3 | 주문 상태가 CREATED가 아님 | 409 ORDER_NOT_PAYABLE |
| 4 | READY/IN_PROGRESS를 합쳐 2건 이상 | 409 PAYMENT_STATE_CONFLICT |
| 5 | DONE/CANCELED 결제 이력 존재 | 409 ORDER_NOT_PAYABLE |
| 6 | 주문 금액이 null 또는 0 이하 | 409 ORDER_NOT_PAYABLE |
| 7 | 활성 결제 금액과 주문 금액 불일치 | 409 PAYMENT_STATE_CONFLICT |

DB 금액 컬럼은 NOT NULL이라 null 검사는 방어 코드다. 이 검증들은 INSERT 전에 끝나며, 예외가 발생하면 트랜잭션을 롤백하고 획득했던 락을 해제한다. 오류 응답이 나더라도 기존 결제를 FAILED로 바꾸지 않는다.

경로 자체를 생략해 매핑된 URL과 달라지는 경우는 위 파라미터 검증과 달리 라우팅 단계에서 처리된다.

## 5. 결제 조회

### 성공: 저장된 사실 읽기

`getPayment()`는 읽기 전용 트랜잭션으로 본인 소유 결제를 조회한다. 대표적인 쿼리 형태는 다음과 같다.

```sql
SELECT p.*
FROM payments p
JOIN orders o ON o.id = p.order_id
WHERE p.id = :paymentId AND o.user_id = :userId;
-- FOR UPDATE 없음

-- Order가 아직 로딩되지 않았다면 orderStatus 접근 시 추가 조회 가능
SELECT * FROM orders WHERE id = :orderId;
```

소유권 조회의 JOIN과 `Payment.order` 엔티티를 fetch하는 것은 같은 뜻이 아니다. LAZY 연관관계 접근 때문에 추가 SELECT가 발생할 수 있으므로, GET이 반드시 SQL 한 번이라고 가정하지 않는다.

200은 **조회 성공**이다. READY, FAILED, IN_PROGRESS도 정상 조회이면 200이다. `paymentKey`는 응답하지 않고, `cancelStatus`는 현재 Payment.CANCELED이면 DONE, 나머지는 NONE으로 만든다. PG 최신 상태를 조회하거나 불확정 결과를 복구하지 않는다.

읽기 전용 설정 자체가 모든 쓰기를 물리적으로 금지하는 보안 장치인 것은 아니다. 현재 메서드가 조회와 DTO 생성만 수행하기 때문에 쓰기가 없다. DB에서 읽은 상태를 반환하며, 응답이 도착한 시점에는 다른 요청이 상태를 바꿨을 수 있다.

### 실패

- 입력 오류: 400 BAD_REQUEST.
- 미존재 결제 또는 다른 사용자 결제: 모두 404 NOT_FOUND.
- DB 접근·트랜잭션 처리 오류: 결제 API 예외 처리기가 503 PAYMENT_RESULT_UNAVAILABLE로 변환한다.
- 어느 경우에도 조회 실패를 이유로 결제를 FAILED 처리하거나 재고를 복구하지 않는다.

## 6. 일반 주문 취소

### 성공: READY 만료와 재고 복구

전제는 재고 7개, 주문 수량 3개, Order.CREATED, Payment.READY다.

1. 소유한 주문을 FOR UPDATE로 조회한다.
2. 결제 이력을 조회하고 IN_PROGRESS/DONE 또는 여러 READY가 없는지 검사한다.
3. 주문상품을 조회한 뒤 `order.cancel()`과 `payment.expire()`로 엔티티를 변경한다.
4. 옵션 ID 오름차순으로 재고를 복구한다.
5. 커밋하면 주문 CANCELED, 결제 EXPIRED, 재고 10이 함께 확정된다.

```sql
-- 트랜잭션 시작
SELECT * FROM orders WHERE id = :orderId AND user_id = :userId FOR UPDATE;
SELECT * FROM payments WHERE order_id = :orderId ORDER BY created_at ASC;
SELECT * FROM order_items WHERE order_id = :orderId ORDER BY id ASC;

-- 엔티티 변경을 flush할 때 실행되는 대표 SQL. 실제 UPDATE 순서는 고정하지 않음
UPDATE orders SET status = 'CANCELED', ... WHERE id = :orderId;
UPDATE payments SET status = 'EXPIRED', ... WHERE id = :paymentId;

UPDATE product_options
SET stock_quantity = stock_quantity + 3
WHERE id = :optionId;
-- COMMIT: 주문 락과 재고 쓰기 락 해제
```

결제 이력이 전혀 없어도 주문 취소는 가능하다. FAILED/EXPIRED 이력은 그대로 두며 비활성 옵션도 복구한다. 이미 주문으로 전환했던 장바구니 항목을 다시 생성하지는 않는다.

### 여기서 flushAutomatically가 하는 일

재고 복구 메서드에 `@Modifying(flushAutomatically = true)`가 있다. 따라서 재고 UPDATE 전에 영속성 컨텍스트의 보류된 변경을 flush한다. **직전에 수정한 ProductOption 하나만 보내는 기능이 아니다.** 이 흐름에서는 주문 CANCELED와 결제 EXPIRED 변경도 먼저 SQL로 반영될 수 있다. [Spring Data JPA Modifying](https://docs.spring.io/spring-data/jpa/docs/3.5.0/api/org/springframework/data/jpa/repository/Modifying.html)

```text
order.cancel(), payment.expire(): Java 엔티티 상태 변경
첫 재고 복구 호출: flush -> 주문/결제 UPDATE -> 재고 UPDATE
둘째 재고 복구 호출: flush -> 추가 변경이 없다면 보낼 엔티티 UPDATE 없음 -> 재고 UPDATE
마지막 commit: 전체 변경 확정
```

flush는 새 트랜잭션을 열거나 커밋하지 않는다. `@Modifying`도 트랜잭션 경계를 만드는 애너테이션이 아니며, 여기서는 서비스의 트랜잭션 안에서 변경 쿼리를 실행하도록 지정한다.

벌크 재고 UPDATE 결과는 관리 중인 ProductOption 객체에 자동 반영되지 않는다. 예를 들어 DB 재고는 10이 됐어도 로딩된 객체에는 7이 남을 수 있다. 현재 취소 응답은 주문상품 스냅샷을 사용하므로 이 오래된 재고를 응답/후속 계산에 쓰지 않는다. `clearAutomatically`는 지정하지 않았으며 기본값은 false다.

### 실패: 취소 금지 상태 또는 복구 실패

| 조건 | 결과 |
| --- | --- |
| 소유 주문 없음 | 404 NOT_FOUND |
| IN_PROGRESS 또는 DONE 결제 존재 | 409 PAYMENT_STATE_CONFLICT, 상태·재고 유지 |
| READY가 2건 이상 | 409 PAYMENT_STATE_CONFLICT, 상태·재고 유지 |
| 이미 CANCELED인 주문 | 앞선 결제 상태 검사를 통과하면 400 BAD_REQUEST, 재복구 없음 |
| 재고 복구 affected rows가 1이 아님 | 409 PAYMENT_STATE_CONFLICT, 전체 롤백 |
| 재고 복구 SQL 오류 | 전체 롤백, 주문 API에는 결제용 503 매핑이 적용되지 않음 |

affected rows 0은 정상적인 주문상품 FK 관계에서는 흔한 상황이 아니다. 코드가 예상하지 못한 갱신 결과를 성공으로 처리하지 않기 위한 방어 검사다.

두 번째 복구가 실패하면 다음처럼 처리된다.

```text
주문 CANCELED UPDATE 실행
결제 EXPIRED UPDATE 실행
옵션 10 재고 +3 UPDATE 성공
옵션 20 재고 UPDATE 실패
  -> 예외가 서비스 밖으로 전달됨
  -> 전체 rollback

DB 최종 상태: 주문 CREATED, 결제 READY, 옵션 10과 20 모두 취소 전 재고
```

앞선 SQL이 이미 flush됐어도 롤백 대상이다. 실제 테스트는 두 번째 옵션의 재고를 INT 최댓값으로 만들어 증가 시 SQL 오류를 유발하고, 첫 번째 복구와 상태 변경이 모두 롤백되는지 검증한다.

## 7. 서로 다른 요청이 겹칠 때

### 준비와 취소가 같은 주문에 들어옴

| 먼저 커밋한 작업 | 뒤따른 작업 | 최종 결과 |
| --- | --- | --- |
| 준비: READY 생성 | 취소: 락 획득 후 READY 만료·재고 복구 | 주문 CANCELED, 결제 EXPIRED |
| 취소: 주문 CANCELED·재고 복구 | 준비: 락 획득 후 주문 상태 거절 | 준비 409, 새 Payment 없음 |

첫 작업이 롤백되면 두 번째 작업은 롤백 후 상태를 보고 판단한다. 응답 전송 순서와 DB 커밋 순서는 다를 수 있으므로, 준비 201을 받은 뒤 조회에서는 이미 EXPIRED일 수도 있다.

### 같은 주문의 취소가 중복됨

A가 주문 락을 획득해 한 번 복구하고 커밋한다. 대기하던 B는 CANCELED 상태를 읽어 거절된다. **두 번의 `+3`을 막는 것은 Atomic UPDATE 자체가 아니라 주문 락과 주문 상태 검증**이다. `+3`이 원자적이어도 두 번 실행하면 `+6`이 된다.

### 서로 다른 주문의 생성·취소가 같은 옵션을 사용함

서로 다른 주문 락을 사용해도 재고 행은 같으므로 해당 옵션 UPDATE에서 경합한다.

```text
초기 재고 7
새 주문 -2, 기존 주문 취소 +3

생성이 먼저 커밋: 7 -> 5 -> 8
취소가 먼저 커밋: 7 -> 10 -> 8
```

위 예시는 두 요청 모두 검증을 통과해 커밋한 경우다. DB의 현재 값을 기준으로 각각 더하고 빼므로 상대방의 변경을 덮어쓰지 않는다. 반면 두 요청이 Java에서 재고 7을 읽어 각각 계산한 값 5와 10을 저장하면 마지막 값만 남는 갱신 유실이 생길 수 있다.

여러 옵션은 ID 오름차순으로 갱신해 옵션 락 획득 순서를 맞춘다. 이는 데드락 위험을 줄이는 조치이지, FK나 다른 경로까지 포함한 모든 데드락을 없앤다는 보장은 아니다.

## 8. DB 장애와 실패 응답을 해석하는 법

| 상황 | DB/응답 해석 | 후속 행동 |
| --- | --- | --- |
| 결제 준비 중 검증 거절 | 의도된 거절, 새 Payment 없음 | 입력·소유권·현재 상태 확인 |
| 준비 INSERT 등 실행 오류, 롤백 확인 | 이번 변경은 취소됨. 결제 API는 503 | 같은 주문으로 준비 재요청 |
| DB 커밋 결과를 통신 오류로 확인 못함 | 503만으로 실제 커밋 여부를 단정할 수 없음 | 같은 주문으로 기존 시도 확인 |
| 준비 커밋 성공 후 HTTP 응답 유실 | DB에는 READY가 남을 수 있음 | 같은 주문 재요청으로 재사용 |
| 결제 GET의 DB 장애 | 상태 조회 불가일 뿐 승인 실패 확정 아님 | 동일 paymentId로 재조회 |

`PaymentExceptionHandler`는 PaymentController에만 적용된다. DataAccessException, 트랜잭션 시작·커밋 관련 지정 예외를 503으로 매핑한다. 모든 예외를 포괄적으로 감싸지는 않는다. 주문 생성·취소의 처리되지 않은 DB 오류는 별도 매핑이 없어 일반 서버 오류 경로로 전달되며, 동일한 `{code: PAYMENT_RESULT_UNAVAILABLE}` 계약이 아니다.

이 문서에서 "전체 롤백"은 트랜잭션 실행 오류에 대한 롤백 처리가 완료된 경우를 말한다. **커밋 응답 유실까지 무조건 롤백됐다고 해석하면 안 된다.** 향후 PG 연동에서도 DB 롤백이 외부 카드 승인을 취소해 주지는 않는다.

## 9. 보장 범위와 남은 작업

- 현재 요청 경로에서는 재고 조건부 차감, 준비 중복 제어, 일반 주문 취소의 한 번 복구를 구현했다. 별도 낙관적 락 재시도나 DB 락 오류 자동 재시도는 없다.
- 주문 생성 API 자체의 멱등성은 별개다. 같은 장바구니에 대한 중복 주문 생성을 막는 전용 락·멱등키는 현재 없다. 재고 음수 방지가 모든 중복 주문 방지를 의미하지 않는다.
- 결제 관련 모든 상태 변경이 같은 주문 락 규칙을 따라야 한다. Repository 직접 저장이나 향후 우회 경로까지 DB 제약만으로 단일 활성 결제를 보장하지는 않는다.
- 일반 주문 취소는 IN_PROGRESS/DONE을 차단하지만, CREATED 주문에 CANCELED 결제 이력이 있는 비정상 조합을 별도로 차단하는 코드는 없다. 향후 PG 취소에서는 결제·주문·재고 상태를 함께 확정하도록 연결해야 한다.
- 필수 헤더 누락·타입 변환·컨트롤러 메서드 검증 오류의 응답은 GlobalExceptionHandler에서 공통 처리한다. 다만 결제 API의 양의 ID 제약을 기존 OrderController에도 추가한 것은 아니다. 현재 `X-USER-ID`는 임시 사용자 식별 수단이며 운영 인증이 아니다.
- PG 승인·전액 취소, Order.PAID, 취소 작업 상태 추적, 미결제 주문 자동 만료, 불확정 결과 복구는 아직 없다. IN_PROGRESS를 경과 시간만으로 실패 처리하거나 재고를 풀면 안 된다.

## 10. 코드와 테스트 찾아보기

| 확인할 내용 | 코드 |
| --- | --- |
| 준비·조회 판단과 트랜잭션 | [PaymentService](../src/main/java/com/marketflow/payment/service/PaymentService.java) |
| 주문 생성·취소 처리 순서 | [OrderService](../src/main/java/com/marketflow/order/service/OrderService.java) |
| 주문 행 비관적 락 | [OrderRepository](../src/main/java/com/marketflow/order/repository/OrderRepository.java) |
| 재고 차감·복구 쿼리 | [ProductOptionRepository](../src/main/java/com/marketflow/product/repository/ProductOptionRepository.java) |
| 상태·응답·입력 검증 | [PaymentControllerTest](../src/test/java/com/marketflow/payment/controller/PaymentControllerTest.java) |
| 동시 준비·취소·재고 복구 롤백 | [PaymentConcurrencyTest](../src/test/java/com/marketflow/payment/service/PaymentConcurrencyTest.java) |
| DB 오류의 HTTP 매핑 | [PaymentExceptionHandlerTest](../src/test/java/com/marketflow/payment/controller/PaymentExceptionHandlerTest.java) |

문서 작성 시 기존 JUnit XML 13개를 재확인한 결과 88 tests, failures/errors/skipped 모두 0이었다. 이번 작업은 문서만 변경했으며 테스트를 새로 실행하지 않았다. DB 오류 HTTP 매핑은 모의 서비스 예외 테스트이고 실제 네트워크 단절·PG 장애 실험은 아니다.

SQL 예시는 전체 실행 로그나 그대로 실행할 스크립트가 아니다. Hibernate의 별칭·열 목록·dirty checking UPDATE 순서·추가 LAZY SELECT는 달라질 수 있다. 트랜잭션 시작/종료도 JDBC 호출로 처리될 수 있어 로그에 문자 그대로 BEGIN/COMMIT이 보인다고 가정하지 않는다. 기존 실행 결과 요약은 [결제 준비·조회 문서](payment-preparation.md), 재고 전략 설명은 [Atomic UPDATE 문서](stock-concurrency-atomic-update.md)를 참고한다.
