# 관리자 계정 정지·주문 거부 경고 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 계정을 정지·재활성화할 수 있고, 구매자·판매자의 주문 취소가 30일 안에 두 번 누적되면 같은 정지 흐름을 재사용해 계정을 자동 정지한다. 정지된 사용자의 활성 Redis 세션은 즉시 종료한다.

**Architecture:** `account`가 정지 상태 변경, 관리자 권한 검사, 활성 세션 종료를 소유한다. `order`는 취소가 최종 승인된 뒤 `OrderCancelledEvent`를 발행하며, `account`의 경고 수신기가 이유별 경고를 원장에 남기고 활성 경고 수를 세어 정지를 요청한다. MySQL·Redis 주문 경로는 기존 이벤트를 공통으로 사용하므로 경고 정책을 각 명령 서비스에 중복 구현하지 않는다. 프론트는 `/admin` 공용 셸 안에 회원 관리와 기존 Stream 복구 탭을 둔다.

**Tech Stack:** Java 21, Spring Boot 4.1, JPA/MySQL, Spring Session Data Redis, React, TypeScript, TanStack Query, Vitest

---

## 확정 정책

- `AccountStatus.SUSPENDED`는 로그인 차단에 이미 사용된다. 이번 작업은 상태를 실제로 변경하고 기존 활성 세션도 삭제한다.
- 수동 정지와 자동 정지는 하나의 `AccountSuspensionService`를 통해 수행한다. 이미 정지된 계정은 멱등적으로 처리한다.
- 경고는 `issued_at + 30일` 동안 활성이다. 만료되어도 행을 삭제하지 않아 감사 이력을 보존한다.
- 새 경고가 발급된 직후 활성 경고 수가 **2개 이상**이면 자동 정지한다.
- 자동 정지 후 경고가 만료돼도 상태는 자동으로 `ACTIVE`로 돌아가지 않는다. 관리자만 재활성화할 수 있다.
- 경고는 주문 상태가 실제로 `CANCELLED`로 전이되고 `OrderCancelledEvent`가 발행된 경우에만 기록한다. 중복 Redis 승인·이벤트 전달에 대비해 `(order_id, cancelled_by)` 유니크 제약 또는 같은 의미의 멱등성 제약을 둔다.
- MySQL 주문 경로에서는 취소가 커밋된 후에만 경고를 발급한다. listener는 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`를 사용하며, Redis 경로처럼 바깥 트랜잭션이 없는 이벤트도 처리한다.
- 경고 발급·활성 수 계산·자동 정지는 대상 `Account` 행을 `PESSIMISTIC_WRITE`로 잠가 하나의 트랜잭션에서 직렬화한다. 동시에 두 취소가 승인되어도 두 번째 발급은 첫 번째 경고를 보고 정지 기준을 판단한다.
- `#469`은 완료되어 Spring Session Data Redis의 `FindByIndexNameSessionRepository.findByPrincipalName(userId.toString())`를 사용한다. 찾아진 모든 세션 ID를 SSE 종료 신호 뒤 Redis repository에서 삭제한다. 자동 정지가 **현재 요청 사용자**에게 발생한 경우에는 repository 삭제에 앞서 현재 `HttpSession.invalidate()`를 호출한다. Spring Session의 요청 종료 저장 단계가 현재 래핑 세션을 Redis에 다시 저장하지 못하게 해 다음 요청을 즉시 인증 해제한다.

## 데이터 모델

`user_warnings`를 새로 만든다.

| 컬럼 | 타입 | 제약/의미 |
|---|---|---|
| `id` | `BIGINT` | PK, auto increment |
| `user_id` | `INT` | `users.id` FK, 경고 대상 |
| `order_id` | `INT` | `orders.id` FK, 같은 취소 중복 발급 방지 근거 |
| `reason` | `VARCHAR(32)` | `BUYER_CANCELLED` 또는 `SELLER_CANCELLED` |
| `issued_at` | `DATETIME` | 발급 시각 |
| `expires_at` | `DATETIME` | 발급 시각 + 30일 |

- `UNIQUE (order_id, reason)`으로 중복 이벤트에도 경고가 한 번만 저장되게 한다.
- 활성 수 조회용 인덱스는 `(user_id, expires_at)`로 둔다.
- 상태 변경에는 별도 이력 테이블을 만들지 않는다. 이번 이슈의 감사 범위는 경고 원장이고, 관리자 정지 사유·행위자 이력은 후속 관리 감사 이슈로 분리한다.

## API 계약

| API | 권한 | 결과 |
|---|---|---|
| `GET /api/admin/users?page=&size=&keyword=` | ADMIN | 회원 목록과 현재 상태, 활성 경고 요약 |
| `GET /api/admin/users/{userId}/warnings` | ADMIN | 대상의 전체 경고 이력 |
| `POST /api/admin/users/{userId}/suspend` | ADMIN | 대상 정지 및 활성 세션 즉시 종료 |
| `POST /api/admin/users/{userId}/activate` | ADMIN | 대상 재활성화 |

- 관리자 자신을 정지·재활성화하는 요청은 서비스에서 거부한다.
- 존재하지 않는 대상은 404, 관리자가 아닌 요청은 기존 admin 예외 응답 방식의 403을 사용한다.
- 정지·재활성화는 반복 호출해도 성공 상태를 반환해 UI 재시도와 이벤트 중복에 안전하게 한다.

## Task 1: 경고 원장과 Account 상태 전이 기반을 만든다

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Modify: `backend/src/main/java/com/dbidding/account/domain/Account.java`
- Modify: `backend/src/main/java/com/dbidding/account/repository/AccountRepository.java`
- Create: `backend/src/main/java/com/dbidding/account/warning/UserWarning.java`
- Create: `backend/src/main/java/com/dbidding/account/warning/UserWarningReason.java`
- Create: `backend/src/main/java/com/dbidding/account/warning/UserWarningRepository.java`
- Test: `backend/src/test/java/com/dbidding/account/warning/UserWarningRepositoryTest.java`

- [ ] `users` 상태를 `SUSPENDED`/`ACTIVE`로 바꾸는 의도 명확한 도메인 메서드를 추가한다. `WITHDRAWN`은 재활성화하지 않는다.
- [ ] `user_warnings` DDL, FK, 유니크 제약, 활성 조회 인덱스를 추가한다.
- [ ] JPA 경고 엔티티·enum·repository를 추가한다. repository는 `expires_at > now`인 활성 경고 수와 `(order_id, reason)` 존재 여부를 조회한다. `AccountRepository`에는 대상 사용자 단위 정책을 직렬화할 `findByIdForUpdate`를 추가한다.
- [ ] repository 통합 테스트로 만료 경고 제외, 중복 취소 무시, 활성 수 계산을 검증한다.

## Task 2: 정지·재활성화와 세션 강제 종료를 구현한다

**Files:**
- Create: `backend/src/main/java/com/dbidding/account/admin/AccountSuspensionService.java`
- Create: `backend/src/main/java/com/dbidding/account/admin/AccountAdminAccessDeniedException.java`
- Modify: `backend/src/main/java/com/dbidding/account/authentication/session/SessionAuthenticationStrategy.java` 또는 세션 종료 책임 전용 collaborator
- Test: `backend/src/test/java/com/dbidding/account/admin/AccountSuspensionServiceTest.java`

- [ ] `requireAdmin(actorId)`를 account 영역에 둔다. 다른 도메인의 repository/entity를 import하지 않는다.
- [ ] `suspend(actorId, targetId)`는 관리자·대상 존재·자기 자신 차단을 검증한 뒤 상태를 `SUSPENDED`로 바꾼다.
- [ ] `activate(actorId, targetId)`는 동일 권한 검증 뒤 `SUSPENDED`만 `ACTIVE`로 바꾼다. `WITHDRAWN`은 오류로 처리한다.
- [ ] 전용 `AccountSessionRevoker`를 두거나 `SessionAuthenticationStrategy`의 재사용 가능한 collaborator를 추출한다. `FindByIndexNameSessionRepository`에서 대상 principal의 세션 ID를 모두 찾고, `SessionSseTerminationPublisher.terminate(sessionId)` 뒤 `deleteById(sessionId)`를 호출한다. `ObjectProvider<HttpServletRequest>`로 현재 요청이 있을 때에는, 현재 session의 `SessionPrincipal`이 대상 userId인지 확인해 먼저 `HttpSession.invalidate()`한다. 이 단계가 없으면 Spring Session이 요청 종료 시 현재 래핑 세션을 다시 저장할 수 있다. 관리자 수동 정지는 자기 자신을 금지하므로 보통 대상 외부 세션만 삭제하고, 자동 정지는 취소 요청을 보낸 대상의 현재 세션까지 확실히 무효화한다.
- [ ] 상태 전환·세션 삭제·SSE 종료, 관리자 아님, 자기 정지, 이미 정지된 멱등 호출을 Mockito 단위 테스트로 검증한다.

## Task 3: 주문 취소 이벤트에서 경고와 자동 정지를 연결한다

**Files:**
- Create: `backend/src/main/java/com/dbidding/account/warning/OrderCancellationWarningListener.java`
- Create: `backend/src/main/java/com/dbidding/account/warning/OrderCancellationWarningService.java`
- Test: `backend/src/test/java/com/dbidding/account/warning/OrderCancellationWarningListenerTest.java`
- Reference: `backend/src/main/java/com/dbidding/order/OrderService.java`
- Reference: `backend/src/main/java/com/dbidding/order/RedisOrderCommandService.java`

- [ ] `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`로 이벤트를 받는다. MySQL 취소가 롤백되면 경고가 발급되지 않고, Redis 모드의 트랜잭션 밖 이벤트도 수신한다.
- [ ] listener는 `OrderCancelledEvent.CancelledBy.BUYER`면 buyer에게 `BUYER_CANCELLED`, `SELLER`면 seller에게 `SELLER_CANCELLED` 경고 발급 service를 호출한다.
- [ ] service의 새 트랜잭션에서 대상 `Account`를 `findByIdForUpdate`로 잠근 뒤 `(order_id, reason)` 중복을 확인·저장·활성 수 조회·자동 정지를 한 번에 수행한다. event consumer의 재전달과 동시 취소가 경고 수를 부풀리거나 정지를 누락하지 않게 한다.
- [ ] 활성 수가 2 이상이면 시스템 actor가 아닌 내부 정지 메서드로 대상 계정을 정지하고 대상의 모든 세션을 끝낸다. 자동 정지는 관리자 권한 검사를 거치지 않는다.
- [ ] `OrderServiceTest`와 `RedisOrderCommandServiceTest`에 각각 취소 확정 뒤 정확한 `OrderCancelledEvent`가 발행되는 검증을 둔다. 경고 정책을 두 서비스에 직접 복제하지 않는다.
- [ ] listener 단위 테스트는 buyer/seller 사유, 만료 경고 제외, 두 번째 활성 경고의 자동 정지, 중복 이벤트 멱등성을 검증한다.

## Task 4: 관리자 회원 API를 제공한다

**Files:**
- Create: `backend/src/main/java/com/dbidding/account/admin/AccountAdminController.java`
- Create: `backend/src/main/java/com/dbidding/account/admin/AccountAdminQueryService.java`
- Create: `backend/src/main/java/com/dbidding/account/admin/dto/AdminAccountPageResponse.java`
- Create: `backend/src/main/java/com/dbidding/account/admin/dto/AdminAccountResponse.java`
- Create: `backend/src/main/java/com/dbidding/account/admin/dto/UserWarningResponse.java`
- Test: `backend/src/test/java/com/dbidding/account/admin/AccountAdminControllerTest.java`

- [ ] 목록은 페이지·검색어를 받고 이메일/닉네임/ID 검색 기준을 명시한다. 상태와 활성 경고 개수, 가장 최근 활성 경고 만료 시각을 함께 반환한다.
- [ ] 경고 이력 API는 사유·발급·만료 시각을 최신순으로 반환한다.
- [ ] controller는 `@CurrentUser`를 받지만 권한 판단은 service에 위임한다.
- [ ] MockMvc로 admin 성공, 일반 사용자 403, 대상 없음 404, 정지·재활성화, 페이지·검색 응답을 검증한다.

## Task 5: `/admin` 공용 셸과 회원 관리 화면을 만든다

**Files:**
- Modify: `frontend/src/app/routePaths.ts`
- Modify: `frontend/src/app/router.tsx`
- Create: `frontend/src/api/adminAccountApi.ts`
- Create: `frontend/src/pages/admin/AdminLayout.tsx`
- Create: `frontend/src/pages/admin/AccountManagementPage.tsx`
- Create: `frontend/src/pages/admin/AdminPage.css`
- Modify: `frontend/src/pages/stream-recovery/index.tsx` (공용 셸로 옮길 수 있는지에 따라 최소 수정)
- Test: `frontend/src/api/adminAccountApi.test.ts`
- Test: `frontend/src/pages/admin/AccountManagementPage.test.tsx`

- [ ] `/admin/users`를 회원 관리 기본 탭으로, `/admin/stream-recovery`를 기존 복구 탭으로 유지한다. `/admin`은 `/admin/users`로 redirect한다.
- [ ] 공용 셸은 탭 네비게이션을 제공하되, 백엔드 403은 기존 Stream Recovery 화면과 같은 관리자 권한 안내 상태로 보인다.
- [ ] 회원 목록은 검색·페이지네이션·상태 badge·활성 경고 요약을 보여준다. 정지/해제는 확인 모달을 거친다.
- [ ] 대상의 경고 이력은 행 확장 또는 상세 모달에서 사유·발급일·만료일을 보여준다.
- [ ] mutation 성공 뒤 목록과 해당 경고 query를 invalidate하고, 실패 메시지는 사용자에게 보여준다.
- [ ] Vitest로 API 요청 경로, 상태별 버튼, 확인 전 mutation 미호출, 성공 후 목록 갱신을 검증한다.

## Task 6: 전체 검증과 운영 확인을 한다

- [ ] Run: `cd backend && ./gradlew clean test`
- [ ] Run: `cd frontend && npm ci && npm run test && npm run typecheck && npm run build`
- [ ] 수동 확인: 관리자 정지 직후 대상의 다음 API 요청이 인증 실패가 되는지, 새 로그인도 `SUSPENDED` 상태로 거부되는지 확인한다.
- [ ] 수동 확인: 구매자/판매자 각각 두 번째 30일 내 취소에서 경고 2건과 자동 정지, 세션 종료를 확인한다.
- [ ] 기존 사용자 파일 및 이 문서만 각각 의도된 커밋에 넣고, 실제 완료 항목과 검증 결과를 문서에 반영한다.

## 제외 범위

- 관리자 정지 사유·행위자·해제 사유의 별도 감사 로그
- 경고에 대한 사용자 이의 제기·알림 UX
- 주문 취소 외의 신고·사기·경매 방해 경고 발급
- 만료 경고에 따른 자동 재활성화

## 구현 결과

- 경고 원장·계정 상태 전이: `241d5363`
- 주문 취소 경고, 2회 누적 자동 정지, Redis 세션/SSE 종료: `a4712a13`
- 관리자 회원 목록·경고 이력·수동 정지/재활성화 API: `2f795dc9`
- `/admin/users` 회원 관리 화면과 확인 모달: `839d478f`
- 백엔드 대상 단위 테스트(정지·세션 종료·경고 listener/service·관리 API/query)는 통과했다. 전체 Gradle 테스트는 로컬 Docker/Testcontainers 부재로 통합 테스트를 실행하지 못했다.
- 프론트 Vitest 46개 파일·265개 테스트, 타입 검사, production build를 통과했다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
