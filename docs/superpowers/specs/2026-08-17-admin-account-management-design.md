# 관리자 회원 관리 확장 설계 (2026-08-17)

브랜치: `feature/470-admin-account-suspension`

## 배경

로컬 docker로 관리자 회원 관리 화면(`/admin/users`)을 확인하는 과정에서 다음 4가지가 필요하다고 확인됨.

1. `회원 관리` / `Stream 복구` 탭 네비게이션이 `AdminUsersPage`에만 있고 `StreamRecoveryPage`에는 없음.
2. (조사 결과 기존 버그 아님) 계정 정지 시 로그인 차단·세션 무효화·입찰 차단은 이미 정상 동작 확인됨(curl로 직접 검증). 재현 보고는 다른 포트(8080)에 떠 있던 별도 백엔드 인스턴스를 테스트한 것으로 확인되어 범위에서 제외.
3. 관리자가 특정 회원에게 직접 "경고"를 줄 수 있는 기능이 없음. 기존 `user_warnings` 테이블은 주문 취소 경고(`BUYER_CANCELLED`/`SELLER_CANCELLED`) 전용으로 `order_id`가 `NOT NULL`이라 그대로는 못 씀.
4. 회원 목록에 상태별(정지된 유저만) / 경고별(경고 받은 유저만) 필터가 없음.

로그인 실패 메시지에서 "정지됨"을 별도로 노출하는 안은 검토했으나, 계정 상태를 노출하면 크리덴셜 스터핑에 힌트를 주는 문제가 있어 현행(통일된 오류 메시지) 유지로 확정.

## 설계

### 1. Nav 공용화

`AdminUsersPage.tsx`에 하드코딩된 `<nav aria-label="관리자 메뉴">…</nav>`를 `frontend/src/components/admin/AdminNav.tsx`(신규)로 분리한다. 현재 경로(`useLocation`)를 기준으로 `active` 클래스를 자동 부여해서 페이지를 늘려도 활성 탭이 어긋나지 않게 한다. `AdminUsersPage`와 `StreamRecoveryPage` 양쪽에서 이 컴포넌트를 쓴다.

### 2. 관리자 수동 경고

- **스키마**: `user_warnings.order_id`를 `NOT NULL` → `NULL` 허용으로 변경. `UNIQUE (order_id, reason)` 제약은 MySQL에서 NULL을 서로 다른 값으로 취급하므로 수동 경고를 여러 번 줘도 충돌하지 않는다.
- **엔티티**: `UserWarning.orderId`를 `@Column(nullable = true)`로, 정적 팩토리에 order_id 없이 발급하는 경로를 추가.
- **enum**: `UserWarningReason`에 `ADMIN_MANUAL` 추가.
- **서비스 재사용**: `OrderCancellationWarningService`의 "저장 + 2회 누적 시 자동 정지" 로직을 `UserWarningIssuer`(신규, `account.warning` 패키지)로 뽑아내고, `OrderCancellationWarningService`와 새 `AdminWarningService`(신규)가 공유한다. 중복 방지(`existsByOrderIdAndReason`)는 `order_id`가 있는 경로에만 적용하고, 수동 경고는 매번 새로 발급한다(관리자가 반복 경고할 수 있어야 하므로).
- **API**: `POST /api/admin/users/{userId}/warn` 추가. `AccountAdminController`에 매핑, `AdminWarningService.issue(actorId, targetId)` 호출. 관리자 권한 검사는 기존 패턴(`requireAdmin`)을 그대로 따른다.
- **프론트**: 회원 목록 행에 "경고" 버튼 추가. 클릭 시 확인 모달(기존 정지/활성화 모달과 동일한 `admin-modal` 패턴 재사용). 대상의 `active_warning_count`가 1이면 "이 경고로 자동 정지됩니다" 문구를 조건부로 보여준다. 발급 후 목록 쿼리 무효화.
- **이력 모달**: `reason` enum → 한글 라벨 매핑(`BUYER_CANCELLED`→"구매자 주문취소", `SELLER_CANCELLED`→"판매자 주문취소", `ADMIN_MANUAL`→"관리자 경고"). `order_id`가 없으면 "주문 #N" 표기를 생략.

### 3. 목록 필터

- **백엔드**: `AccountAdminQueryService.findAccounts`에 `AccountStatus status`(nullable), `boolean onlyWarned` 파라미터 추가. `AccountRepository.searchForAdmin` JPQL에 다음 조건 추가:
  - `AND (:status IS NULL OR account.status = :status)`
  - `AND (:onlyWarned = FALSE OR EXISTS (SELECT 1 FROM UserWarning w WHERE w.userId = account.id AND w.expiresAt > :now))`
  - `now`는 서비스에서 주입(기존 `nowSupplier` 재사용).
- **컨트롤러**: `GET /api/admin/users`에 `status`(옵션, `ACTIVE`/`SUSPENDED`), `only_warned`(옵션, boolean) 쿼리 파라미터 추가.
- **프론트**: 검색창 옆에 상태 드롭다운(전체/정지됨)과 "경고 있음" 토글 버튼을 추가해서 키워드와 함께 조합 적용한다.

## 테스트 계획

기존 컨벤션(Mockito 단위 테스트, `WebMvcTest` 컨트롤러 테스트, Korean 테스트 메서드명)을 따라 다음을 추가/보강한다.

- `UserWarningIssuerTest`: 신규 공유 로직의 저장/중복방지/자동정지 분기.
- `AdminWarningServiceTest`: 관리자 권한 체크, 발급 위임.
- `AccountAdminControllerTest`: `/warn` 엔드포인트, `status`/`only_warned` 쿼리 파라미터 반영된 `findAccounts` 위임.
- `AccountAdminQueryServiceTest`: 필터 조합 쿼리 위임 검증(리포지토리 쿼리 자체는 통합 테스트 또는 기존 `UserWarningRepositoryTest` 패턴 참고).

## 범위 밖

- 로그인 실패 메시지 세분화(보안상 보류).
- 경고 사유 자유 텍스트 메모.
- 정지/경고 사유별 통계·대시보드.
