# 재고 차감 동시성 제어: 낙관적 락과 재시도

## 배경

주문 생성은 장바구니 항목을 주문으로 전환하면서 `ProductOption.stockQuantity`를 차감한다. 비관적 락은 같은 상품 옵션 row를 먼저 잠그고 뒤 요청을 기다리게 만드는 방식이었다. 이번 실험은 `@Version` 기반 낙관적 락으로 전환해, 동시에 같은 재고를 수정한 트랜잭션을 커밋 시점에 충돌로 감지하는 방식이다.

## 충돌 감지 방식

`ProductOption`에 `@Version` 필드를 추가했다.

```java
@Version
private Long version;
```

Hibernate는 재고 UPDATE 시 version 조건을 함께 사용한다. 먼저 커밋한 트랜잭션이 version을 증가시키면, 오래된 version을 들고 있던 다른 트랜잭션의 UPDATE는 반영 row 수가 0이 되고 `OptimisticLockingFailureException` 계열 예외로 변환된다.

## 재시도 없는 낙관적 락의 한계

낙관적 락은 충돌을 막는 잠금이 아니라 충돌을 감지하는 장치다. 재고가 충분한 상황에서도 동시에 같은 version을 읽은 주문 중 일부는 실패할 수 있다. 따라서 사용자 10명이 재고 10개를 각각 1개씩 주문하는 시나리오에서는 단순히 예외를 반환하면 실제로 처리 가능한 주문도 실패한다.

## 주문 생성 전체 재시도

`createOrder()`는 트랜잭션을 열지 않는 retry wrapper로 두고, 실제 주문 생성은 `TransactionTemplate` 안에서 실행한다. 이렇게 해야 실패한 시도의 영속성 컨텍스트와 DB 변경을 모두 롤백한 뒤 새 트랜잭션에서 장바구니, 상품 옵션, 재고 version을 다시 읽을 수 있다.

재시도 정책은 다음과 같다.

```text
최대 시도: 10회
재시도 예외: OptimisticLockingFailureException
backoff: 10~50ms 랜덤 대기
최종 실패: OptimisticLockingFailureException 그대로 전파
```

충돌한 요청들이 즉시 같은 타이밍에 재시도하면 다시 같은 version을 읽고 충돌할 수 있다. 짧은 랜덤 backoff를 넣어 재시도 타이밍을 분산한다.

재시도는 주문 생성에만 적용했다. 주문 취소 시 재고 복구 충돌 처리는 별도 실험 대상으로 남긴다.

## flush 위치

재고 차감 직후 `ProductOptionRepository.flush()`를 호출한다.

```java
decreaseStock(cartItems);
productOptionRepository.flush();
```

목적은 `product_options` version UPDATE를 `order_items` INSERT보다 먼저 실행하는 것이다. 충돌을 빠르게 감지하면 해당 시도의 `Order`, `OrderItem`, 장바구니 삭제는 같은 트랜잭션 안에서 롤백된다. Repository `flush()`를 사용해 낙관적 락 예외가 Spring의 `OptimisticLockingFailureException` 계층으로 변환되도록 한다.

## 테스트

`ProductOptionStockConcurrencyTest`는 여러 트랜잭션이 같은 상품 옵션을 읽고 동시에 차감할 때 일부 성공과 일부 낙관적 락 실패가 발생하고, 최종 재고가 성공 수만큼만 차감되는지 확인한다.

`OrderServiceConcurrencyTest`는 재고 10개에 사용자 10명이 동시에 1개씩 주문할 때 재시도 후 주문 10건, 주문상품 10건, 장바구니 0건, 최종 재고 0을 기대한다.

추가로 재고 1개에 사용자 10명이 동시에 주문하는 테스트를 두었다. 최종 성공 주문은 1건이고, 재고는 0 아래로 내려가지 않는다.

`@Tag("contention")`이 붙은 경합 관찰 테스트는 같은 상품 옵션에 여러 요청을 동시에 넣고 아래 형태의 로그를 남긴다.

```text
[same-option-contention] requests=100, initialStock=10, success=..., stockShortageFailure=..., retryExhaustedFailure=..., unexpectedFailure=0, remainingStock=..., orders=..., orderItems=..., cartItems=..., elapsedMillis=...
```

각 값은 다음 기준으로 해석한다.

- `success`: 주문 생성까지 완료된 요청 수. 항상 최초 재고 이하이어야 한다.
- `stockShortageFailure`: 재시도 중 최신 재고를 다시 읽었을 때 이미 재고가 부족해서 실패한 요청 수.
- `retryExhaustedFailure`: 낙관적 락 충돌이 최대 시도 횟수까지 반복되어 실패한 요청 수.
- `unexpectedFailure`: 테스트가 분류하지 않은 실패 수. 0이어야 한다.
- `remainingStock`: 최종 상품 옵션 재고. `initialStock - success`와 같고 음수가 아니어야 한다.
- `elapsedMillis`: 해당 시나리오의 전체 동시 주문 처리 시간. 스레드 스케줄링과 DB 타이밍 영향을 받으므로 참고값으로만 본다.

`100 requests / stock 10` 시나리오는 정확히 몇 건이 재고 부족과 retry 소진으로 나뉘는지를 고정하지 않는다. 핵심 관찰값은 성공 주문이 최대 10건으로 제한되고, 주문/주문상품/장바구니 수와 최종 재고가 성공 수에 맞게 보존되며, 재고가 음수가 되지 않는다는 점이다.

아래는 로컬 MySQL 테스트 DB에서 확인한 실행 로그 예시다.

```text
[same-option-contention] requests=5, initialStock=5, success=5, stockShortageFailure=0, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=5, orderItems=5, cartItems=0, elapsedMillis=128
[same-option-contention] requests=10, initialStock=10, success=10, stockShortageFailure=0, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=10, orderItems=10, cartItems=0, elapsedMillis=160
[same-option-contention] requests=30, initialStock=30, success=29, stockShortageFailure=0, retryExhaustedFailure=1, unexpectedFailure=0, remainingStock=1, orders=29, orderItems=29, cartItems=1, elapsedMillis=391
[same-option-contention] requests=50, initialStock=50, success=49, stockShortageFailure=0, retryExhaustedFailure=1, unexpectedFailure=0, remainingStock=1, orders=49, orderItems=49, cartItems=1, elapsedMillis=381
[same-option-contention] requests=100, initialStock=10, success=10, stockShortageFailure=90, retryExhaustedFailure=0, unexpectedFailure=0, remainingStock=0, orders=10, orderItems=10, cartItems=90, elapsedMillis=135
```

이 실행에서는 재고와 요청 수가 같은 5/5, 10/10은 모두 성공했다. 30/30, 50/50은 retry 소진 요청이 1건씩 발생했지만 성공 수만큼만 재고가 차감되어 남은 재고와 장바구니 수가 일치했다. 100/10은 10건만 성공하고 나머지 90건은 재고 부족으로 실패해, 과판매 없이 재고 0을 유지했다.

```bash
./gradlew test --tests "*ProductOptionStockConcurrencyTest"
./gradlew test --tests "*OrderServiceConcurrencyTest"
./gradlew test
```

## 다음 실험 후보

- 분산락으로 애플리케이션 인스턴스 간 재고 차감 직렬화
- 주문 생성 재시도 횟수와 backoff 튜닝
- 주문 취소 재고 복구의 낙관적 락 및 재시도 적용
- 주문 번호 중복 insert 발생 시 재생성/재시도 정책 정리
