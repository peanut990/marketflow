# 재고 차감 동시성 제어: Atomic UPDATE

## 배경

주문 생성은 장바구니 항목을 주문으로 전환하면서 `ProductOption.stockQuantity`를 차감한다. Atomic UPDATE 방식은 Java에서 재고 값을 읽고 계산한 뒤 저장하지 않고, DB의 단일 UPDATE 문 안에서 재고 확인과 차감을 함께 수행한다.

## 조건부 UPDATE

재고 차감은 repository의 조건부 UPDATE로 처리한다.

```java
@Modifying(flushAutomatically = true)
@Query("""
        update ProductOption productOption
        set productOption.stockQuantity = productOption.stockQuantity - :quantity
        where productOption.id = :id
            and productOption.active = true
            and productOption.stockQuantity >= :quantity
        """)
int decreaseStockAtomically(Long id, int quantity);
```

affected rows가 `1`이면 재고 차감 성공이고, `0`이면 재고가 부족하거나 주문 가능한 옵션이 아니므로 실패로 본다. 주문 생성 흐름에서는 이미 비활성 옵션을 별도로 검증하므로, Atomic UPDATE 실패는 `InsufficientStockException`으로 처리한다.

## 정합성 보장 방식

`stockQuantity >= quantity` 조건이 UPDATE 문 안에 포함되어 있으므로 재고 확인과 차감이 하나의 DB 작업으로 묶인다. 동일한 row에 여러 UPDATE가 동시에 들어오면 InnoDB는 해당 row에 X Lock을 잡고 UPDATE를 순서대로 처리한다.

예를 들어 재고가 1개일 때 두 요청이 동시에 `quantity = 1`을 차감하려고 하면, 먼저 처리된 UPDATE만 affected rows `1`을 반환한다. 다음 UPDATE는 최신 재고 0을 기준으로 `stockQuantity >= 1` 조건을 만족하지 못해 affected rows `0`을 반환한다. 그래서 재고가 음수가 되지 않는다.

## 주문 생성 흐름

`createOrder()`는 하나의 트랜잭션 안에서 처리한다.

```text
장바구니 조회
주문 가능 검증
총 주문 금액 계산
Atomic UPDATE 재고 차감
주문 저장
주문상품 저장
장바구니 삭제
```

다중 옵션 주문에서는 상품 옵션 id 오름차순으로 Atomic UPDATE를 실행한다. 두 번째 옵션 차감에서 재고 부족이 발생하면 예외가 발생하고, 같은 트랜잭션 안에서 먼저 성공했던 옵션 차감도 함께 롤백된다.

## 테스트

`ProductOptionStockConcurrencyTest`는 주문 흐름을 제외하고 repository Atomic UPDATE만 동시에 실행한다. 재고 10개에 20개 차감 요청을 보내면 성공 10건, 재고 부족 실패 10건, 최종 재고 0을 기대한다.

`OrderServiceConcurrencyTest`는 기존 경합 관찰 로그 형식을 유지한다.

```text
[same-option-contention] requests=100, initialStock=10, success=..., stockShortageFailure=..., retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=..., orders=..., orderItems=..., cartItems=..., elapsedMillis=...
```

Atomic UPDATE 방식에서는 낙관적 락 retry를 사용하지 않으므로 `retryExhaustedFailure`는 0이어야 한다. `100 requests / stock 10` 시나리오의 핵심 관찰값은 성공 주문이 최대 10건이고, 나머지는 재고 부족으로 실패하며, 최종 재고가 0 아래로 내려가지 않는다는 점이다.

아래는 로컬 MySQL 테스트 DB에서 확인한 실행 로그 예시다.

```text
[same-option-contention] requests=5, initialStock=5, success=5, stockShortageFailure=0, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=5, orderItems=5, cartItems=0, elapsedMillis=28
[same-option-contention] requests=10, initialStock=10, success=10, stockShortageFailure=0, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=10, orderItems=10, cartItems=0, elapsedMillis=45
[same-option-contention] requests=30, initialStock=30, success=30, stockShortageFailure=0, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=30, orderItems=30, cartItems=0, elapsedMillis=109
[same-option-contention] requests=50, initialStock=50, success=50, stockShortageFailure=0, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=50, orderItems=50, cartItems=0, elapsedMillis=157
[same-option-contention] requests=100, initialStock=10, success=10, stockShortageFailure=90, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=10, orderItems=10, cartItems=90, elapsedMillis=66
```

이 실행에서는 재고와 요청 수가 같은 5/5, 10/10, 30/30, 50/50은 모두 성공했다. 100/10은 10건만 성공하고 나머지 90건은 재고 부족으로 실패해, 과판매 없이 재고 0을 유지했다.

```bash
./gradlew test --tests "*ProductOptionStockConcurrencyTest"
./gradlew test --tests "*OrderServiceConcurrencyTest"
./gradlew test
```

## 비교 포인트

- 낙관적 락은 version 충돌을 감지한 뒤 주문 생성 전체를 재시도한다.
- Atomic UPDATE는 재고 조건과 차감을 한 UPDATE에 묶어 affected rows로 성공 여부를 판단한다.
- 단순 재고 차감에서는 Atomic UPDATE가 retry/backoff 없이 정합성을 설명하기 쉽다.
- 복잡한 도메인 상태 전이가 함께 필요한 경우에는 UPDATE 조건과 트랜잭션 경계를 더 신중히 설계해야 한다.
