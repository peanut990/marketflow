# 재고 차감 동시성 제어: 비관적 락 적용 기록

## 배경

주문 생성 API는 장바구니 항목을 주문으로 전환하면서 `ProductOption.stockQuantity`를 차감한다.

기존 흐름은 트랜잭션 안에서 장바구니 항목을 조회하고, 상품 옵션 재고를 검증한 뒤 `decreaseStock()`으로 엔티티 필드 값을 변경하는 방식이었다.

```java
List<CartItem> cartItems = getCartItems(user.getId(), cartItemIds);

validateOrderableCartItems(cartItems);

Order order = orderRepository.save(new Order(generateOrderNo(), user, totalAmount));

decreaseStock(cartItems);

List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);
cartItemRepository.deleteAll(cartItems);
```

단일 요청에서는 문제가 없지만, 같은 상품 옵션에 주문이 동시에 몰리면 여러 트랜잭션이 같은 재고 값을 읽고 각각 차감할 수 있다. 이 경우 실제 주문 수와 재고 차감 결과가 맞지 않는 갱신 손실 문제가 생길 수 있다.

## OrderServiceConcurrencyTest

먼저 실제 주문 생성 흐름에서 동시성 문제가 재현되는지 확인하기 위해 `OrderServiceConcurrencyTest`를 작성했다.

테스트 조건은 다음과 같다.

```text
사용자 수: 10
초기 재고: 10
각 주문 수량: 1
기대 결과: 주문 10건 성공, 주문상품 10건 생성, 장바구니 0건, 최종 재고 0
```

테스트는 `CountDownLatch`로 10개 스레드가 최대한 동시에 `orderService.createOrder()`를 호출하게 만들었다.

```java
readyLatch.countDown();
startLatch.await();
orderService.createOrder(
        orderAttempt.userId(),
        new OrderCreateRequest(List.of(orderAttempt.cartItemId()))
);
successCount.incrementAndGet();
```

이 테스트는 실제 주문 생성 흐름을 타기 때문에 재고 차감뿐 아니라 `Order`, `OrderItem`, `CartItem` 처리까지 함께 검증한다.

처음 의도는 이 테스트에서 갱신 손실을 확인하는 것이었다. 하지만 실행 결과 갱신 손실만 깔끔하게 보이지 않고, MySQL deadlock이 함께 발생했다.

```text
Deadlock found when trying to get lock; try restarting transaction
```

## deadlock 원인

`decreaseStock()`은 호출 즉시 UPDATE 쿼리를 실행하는 것이 아니라, 영속 상태의 `ProductOption` 필드 값을 변경한다. 실제 UPDATE는 보통 트랜잭션 flush/commit 시점에 실행된다.

반면 `Order`와 `OrderItem`은 `GenerationType.IDENTITY`를 사용하므로, id를 얻기 위해 `save()` 또는 `saveAll()` 과정에서 INSERT가 더 일찍 실행될 수 있다.

`OrderItem`은 `product_option_id`로 `ProductOption`을 참조한다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_option_id", nullable = false)
private ProductOption productOption;
```

InnoDB는 자식 row인 `order_items`를 INSERT할 때 부모 row인 `product_options`가 존재하는지 확인하기 위해 공유락을 잡을 수 있다. 여러 트랜잭션이 같은 옵션에 대해 `order_items` INSERT를 먼저 수행하고, 이후 같은 `product_options` row를 UPDATE하려고 하면 다음 흐름이 생긴다.

```text
T1: order_items INSERT -> product_options에 S락
T2: order_items INSERT -> product_options에 S락
T1: product_options UPDATE를 위해 X락 요청 -> T2의 S락 때문에 대기
T2: product_options UPDATE를 위해 X락 요청 -> T1의 S락 때문에 대기
```

같은 트랜잭션 안에서 S락과 X락이 충돌하는 것이 아니라, 다른 트랜잭션이 잡은 S락 때문에 X락 획득이 막히는 구조다.

결국 `OrderServiceConcurrencyTest`는 실제 주문 흐름의 동시성 문제를 잘 드러내지만, 갱신 손실만 분리해서 보기에는 적합하지 않았다. 주문상품 INSERT와 FK 검사로 인한 DB 락까지 함께 섞이기 때문이다.

## ProductOptionStockConcurrencyTest

갱신 손실 자체를 분리해서 확인하기 위해 `ProductOptionStockConcurrencyTest`를 별도로 작성했다.

이 테스트는 주문을 만들지 않는다. `Order`, `OrderItem`, `CartItem` 흐름을 모두 제외하고 `ProductOption` 재고만 동시에 조회하고 차감한다.

테스트 조건은 다음과 같다.

```text
동시 작업 수: 10
초기 재고: 10
각 작업 차감 수량: 1
정상 기대 재고: 0
락이 없을 때 관찰 값: 0이 아닌 값
```

각 스레드는 `TransactionTemplate`으로 독립 트랜잭션을 열고, 같은 `ProductOption`을 조회한다. 그 뒤 모든 트랜잭션이 조회를 마칠 때까지 기다렸다가 동시에 `decreaseStock(1)`을 호출한다.

```java
transactionTemplate.executeWithoutResult(status -> {
    ProductOption foundProductOption = productOptionRepository.findById(productOption.getId())
            .orElseThrow();

    readLatch.countDown();
    await(readLatch);

    foundProductOption.decreaseStock(1);
});
```

이렇게 하면 여러 트랜잭션이 같은 재고 값을 읽은 뒤 각자 같은 방식으로 값을 덮어쓰는 상황을 만들 수 있다. 주문상품 INSERT가 없으므로 FK 공유락으로 인한 deadlock도 분리된다.

이 테스트는 갱신 손실 확인용으로 통과하도록 작성했다.

```java
assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
assertThat(failures).isEmpty();
assertThat(updatedProductOption.getStockQuantity()).isNotZero();
```

즉 10개 작업이 모두 성공했는데도 최종 재고가 0이 아니라면, 락 없는 재고 차감에서 갱신 손실이 발생한 것이다.

```bash
./gradlew test --tests "*ProductOptionStockConcurrencyTest"
```

## 비관적 락 적용

실제 주문 생성 흐름에서는 갱신 손실과 deadlock을 함께 막아야 한다. 해결 방향은 재고 검증 전에 `ProductOption` row를 먼저 `PESSIMISTIC_WRITE`로 조회하는 것이다.

Hibernate/MySQL 기준으로는 `select ... for update` 형태의 locking read가 실행되고, 해당 row에 배타적 잠금이 걸린다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select po
        from ProductOption po
        join fetch po.product
        where po.id in :ids
        order by po.id asc
        """)
List<ProductOption> findAllByIdInWithPessimisticLock(@Param("ids") List<Long> ids);
```

주문 생성 흐름은 다음 순서로 바꿨다.

```text
1. 사용자와 장바구니 항목 조회
2. 주문 대상 productOptionId 추출
3. product_options row를 PESSIMISTIC_WRITE로 조회
4. locked ProductOption 기준으로 재고 검증
5. locked ProductOption 기준으로 총액 계산
6. locked ProductOption 재고 차감
7. OrderItem 생성
8. 장바구니 삭제
```

핵심은 `order_items` INSERT보다 먼저 재고 row에 쓰기 잠금을 잡는 것이다.

```text
기존 흐름:
ProductOption 조회 -> 재고 값 변경 -> OrderItem INSERT/FK 검사 -> ProductOption UPDATE

수정 흐름:
CartItem 조회 -> ProductOption SELECT FOR UPDATE -> 재고 검증/차감 -> OrderItem INSERT
```

이렇게 하면 같은 상품 옵션에 대한 주문은 `ProductOption` row lock 앞에서 순서대로 처리된다. 뒤 트랜잭션은 앞 트랜잭션이 커밋할 때까지 기다린 뒤 최신 재고를 읽고 다음 차감을 수행한다.

## 적용 중 발견한 문제

처음에는 기존 `CartItemRepository`의 `@EntityGraph` 조회를 그대로 사용했다.

```java
@EntityGraph(attributePaths = {"productOption", "productOption.product"})
List<CartItem> findByUserIdAndIdInOrderByIdAsc(Long userId, List<Long> ids);
```

이 경우 `CartItem` 조회 시점에 이미 `ProductOption`이 영속성 컨텍스트에 올라간다. 이후 비관적 락 조회를 해도 같은 트랜잭션의 1차 캐시에 있는 엔티티가 재사용될 수 있고, 락 대기 이후의 최신 재고 값이 아니라 락 이전에 읽은 값을 기준으로 차감할 수 있다.

실제로 첫 적용 후에는 모든 주문이 성공했지만 최종 재고가 `9`로 남았다.

```text
successCount = 10
failureCount = 0
expected stock = 0
actual stock = 9
```

그래서 주문 생성 전용 장바구니 조회를 분리했다. 이 조회는 `CartItem`만 조회하고, `ProductOption`은 비관적 락 조회에서 처음 로딩되도록 한다.

```java
List<CartItem> findAllByUserIdAndIdInOrderByIdAsc(Long userId, List<Long> ids);
```

기존 메서드와 조회 조건은 같지만, 메서드명을 다르게 둬서 `@EntityGraph`가 붙지 않은 Spring Data JPA derived query로 실행되게 했다. 이렇게 하면 주문 생성에서는 `CartItem`만 먼저 조회하고, `ProductOption`은 이후 비관적 락 조회에서 로딩된다.

`findAllByIdInWithPessimisticLock()`에서 `product`를 fetch join한 이유는 `OrderItem` 생성자에서 상품명 스냅샷을 만들 때 `productOption.getProduct().getName()`을 사용하기 때문이다. 락 조회 시 필요한 상품 정보를 같이 가져오면 주문상품 생성 중 추가 select를 줄일 수 있다.

## 테스트

### 주문 생성 동시성 테스트

`OrderServiceConcurrencyTest`는 실제 주문 생성 흐름을 동시에 호출한다.

```text
사용자 수: 10
초기 재고: 10
각 주문 수량: 1
기대 결과: 주문 10건 성공, 주문상품 10건 생성, 장바구니 0건, 최종 재고 0
```

비관적 락 적용 전에는 deadlock 또는 재고 불일치가 발생할 수 있었다. 비관적 락 적용 후에는 같은 상품 옵션에 대한 주문이 `select ... for update` 앞에서 순서대로 처리되어 테스트가 통과했다.

```bash
./gradlew test --tests "*OrderServiceConcurrencyTest"
```

### 갱신 손실 관찰 테스트

`ProductOptionStockConcurrencyTest`는 주문 흐름을 제외하고 `ProductOption` 재고만 동시에 차감한다.

```text
동시 작업 수: 10
초기 재고: 10
각 작업 차감 수량: 1
정상 기대 재고: 0
락이 없을 때 관찰 값: 0이 아닌 값
```

이 테스트는 일부러 비관적 락을 사용하지 않고 `findById()`를 사용한다. 주문 서비스의 락 적용 여부와 별개로, 락 없는 재고 변경에서 갱신 손실이 발생할 수 있다는 비교군으로 남겨둔다.

```bash
./gradlew test --tests "*ProductOptionStockConcurrencyTest"
```

### 전체 테스트

기존 주문 생성, 주문 조회/취소, 상품, 장바구니 테스트까지 함께 확인한다.

```bash
./gradlew test
```

## 정리

비관적 락은 충돌이 발생한 뒤 실패시키는 방식이 아니라, 충돌 가능성이 있는 row를 먼저 잠그고 뒤 트랜잭션을 기다리게 만드는 방식이다.

이번 적용에서는 재고 검증 전에 `ProductOption`을 `PESSIMISTIC_WRITE`로 조회해서 같은 상품 옵션에 대한 주문을 직렬화했다. 처리량은 줄어들 수 있지만 재고 정합성을 단순하고 명확하게 지킬 수 있다.

## 다음 실험 후보

이 문서는 비관적 락 적용 기록이다. 이후 다음 방식도 같은 시나리오로 비교해볼 수 있다.

- 낙관적 락: 충돌 감지 방식과 재시도 정책을 확인한다.
- 분산락: Redis 등을 이용해 애플리케이션 인스턴스 간 주문 재고 차감을 직렬화한다.
- 재시도 정책: deadlock, optimistic lock failure, lock timeout 발생 시 어느 계층에서 몇 번 재시도할지 정한다.
- 취소 동시성: 주문 취소 시 재고 복구에도 동일한 락 전략이 필요한지 검증한다.
