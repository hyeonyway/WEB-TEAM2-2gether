# 이슈 261 — Upload/Notification/Order/Wallet/Account 시간 처리 Clock 주입 및 Instant 전환

담당: 임하민. 이슈: [#261](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/261)
(브랜치 `refactor/261-clock-injection-and-instant`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, 이번 작업은
서버 전체의 `LocalDateTime`/`Instant` 혼용을 정리하는 시리즈(#261~#264)의 일부로 사용자가
채팅에서 직접 이슈를 나누고 "261부터 시작해줘"라고 명시적으로 지시해서 `order`/`wallet`/`account`
패키지까지 진행한다.

## 배경

서버가 `LocalDateTime`과 `Instant`를 혼용하고 있어 실행 환경(로컬/운영/CI)에 따라 결과가
달라질 수 있는 리스크가 있다. 최종 목표는 `Instant`로 통일하는 것이고, 이 이슈는 그중
상대적으로 독립적이고 위험도가 낮은 4개 패키지를 처리한다. Auction(#262)·Statistic(#263)·
프론트(#264)는 별도 이슈로 진행한다.

## 변경 대상

### 1. Clock 미주입 지점 (JVM 기본 타임존에 의존)

프로젝트는 `TimeConfig`가 `Clock.system(ZoneOffset.UTC)` 빈을 제공하고,
`AuctionCommandService`/`SessionAuthenticationStrategy`가 이미 `private final Clock clock`을
주입받아 `clock.instant()`/`LocalDateTime.now(clock)`을 쓰는 관례가 있다. 아래 4곳은 이
관례를 따르지 않고 시스템 기본 시계를 직접 호출한다.

| 파일 | 현재 | 변경 |
|---|---|---|
| `upload/service/UploadService.java:51` | `LocalDate.now()` | `Clock` 주입 후 `LocalDate.now(clock)` |
| `wallet/service/WalletService.java:160,189` | `Instant.now()` | `Clock` 주입 후 `clock.instant()` |
| `account/authentication/jwt/JwtRefreshService.java:39` | `Instant.now()` | `Clock` 주입 후 `clock.instant()` |
| `account/authentication/jwt/JwtAuthenticationStrategy.java:31` | `Instant.now()` | `Clock` 주입 후 `clock.instant()` |

`WalletService`는 `@RequiredArgsConstructor`라 필드 추가만으로 Spring이 자동 주입한다.
`JwtRefreshService`/`JwtAuthenticationStrategy`는 `@Configuration` 클래스
(`JwtAuthenticationConfiguration`)에서 `new`로 수동 생성되므로 그 빈 메서드에도 `Clock`
파라미터를 추가해서 넘겨줘야 한다. `UploadService`는 명시적 생성자라 파라미터를 추가한다.

### 2. LocalDateTime ↔ Instant 혼용 (엔티티 → DTO 경계)

`Notification.createdAt`, `Order.createdAt`은 둘 다 `@CreationTimestamp`로 Hibernate가
자동 채우는 필드라 애플리케이션 코드에서 직접 `.now()`를 호출하지 않는다. 엔티티 필드
타입만 `LocalDateTime` → `Instant`로 바꾸면 된다(스키마는 이미 `TIMESTAMP(6)`라 변경 불필요,
Hibernate가 `@CreationTimestamp` + `Instant`를 네이티브로 지원 — `WalletHold.createdAt`이
이미 이 패턴).

- `notification/Notification.java:52`
- `order/Order.java:49`

전환 후 `NotificationResponse`/`OrderResponse`가 쓰던 `UtcTime.toInstant()` 경계 변환
호출은 더 이상 필요 없으므로 제거한다(엔티티가 이미 `Instant`를 들고 있음).

## 영향받는 테스트

- `UploadServiceTest` — `new UploadService(presignedUrlProvider)` 2곳에 `Clock` 인자 추가
- `WalletServiceHoldTest`, `WalletServiceCaptureTest`, `WalletServiceBalanceTest`,
  `WalletServiceTransactionTest`, `WalletServiceProvisioningTest` — `new WalletService(...)`
  각 1곳에 `Clock` 인자 추가 (`WalletCaptureIntegrationTest`/`WalletTransactionConcurrencyTest`는
  스프링 컨텍스트로 `Clock` 빈을 자동 주입받으므로 변경 불필요)
- `AuthServiceRefreshTest` — `new JwtRefreshService(...)`에 `Clock` 인자 추가
- `JwtAuthenticationStrategyTest` — `new JwtAuthenticationStrategy(...)`에 `Clock` 인자 추가
- `Notification`/`Order`의 `createdAt`을 직접 세팅하거나 assert하는 테스트는 없음(grep 확인) —
  리포지토리 쿼리도 `createdAt` 기준 필터/정렬이 없어 추가 수정 불필요

## 결과

- 계획대로 두 커밋으로 진행했다. `WalletService`에 서브클래스로 훅을 거는
  `AuctionBidWalletLockOrderConcurrencyTest`의 `CoordinatedWalletService`는 grep에서
  누락되어 컴파일 시점에 발견, `Clock` 파라미터를 추가해 수정했다(문서 작성 시점에는
  예상하지 못한 영향 범위).
- 컴파일(`compileJava`/`compileTestJava`) 통과.
- 영향받는 테스트 전부 통과: `UploadServiceTest`, `WalletServiceHoldTest`,
  `WalletServiceCaptureTest`, `WalletServiceBalanceTest`, `WalletServiceTransactionTest`,
  `WalletServiceProvisioningTest`, `AuthServiceRefreshTest`, `JwtAuthenticationStrategyTest`,
  `AuctionBidWalletLockOrderConcurrencyTest`(Testcontainers 통합 테스트),
  `OrderServiceTest`, `OrderControllerTest`, `NotificationServiceBulkInsertTest`,
  `NotificationControllerTest`, `NotificationServiceTest`,
  `NotificationReconciliationServiceTest`, `AuctionResultNotificationRecoverySchedulerTest`,
  `UrgentNotificationRecoverySchedulerTest`.

## 커밋 이력

1. `refactor: Upload/Wallet/Jwt 서비스에 Clock 주입`
2. `refactor: Notification/Order createdAt을 LocalDateTime에서 Instant로 전환`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
