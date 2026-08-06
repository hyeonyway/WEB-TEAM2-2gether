# 이슈 6: 최종 인증 방식 선택과 미선택 구현 제거

## 1. 이슈 경계

- 선행 문서: [`5-benchmark-and-failure-test.md`](5-benchmark-and-failure-test.md)
- 목표: 측정 결과로 하나의 인증 방식을 선택하고 실험용 이중 구현 제거
- 포함: 의사결정 기록, 배포·롤백, 코드·설정·데이터 정리, 최종 검증
- 비목표: JWT와 세션을 장기적으로 동시에 운영

이 이슈를 시작하기 전에는 두 구현을 삭제하지 않는다. 기능·보안·부하·장애 실험 결과가 준비돼야 한다.

## 2. 의사결정 기록

최종 선택 기록에는 다음을 포함한다.

- 선택한 방식과 정확한 구성
- 선택하지 않은 방식
- 비교한 커밋과 실행 환경
- 기능·보안 보장 차이
- TPS·p95·p99·오류율
- Redis 장애와 애플리케이션 장애 결과
- 개인화 SSE 인증·복구 복잡성
- 운영·모니터링 비용
- 미래 도메인 분리에 대한 판단
- 기각한 대안과 기각 이유
- 남아 있는 위험과 후속 조치

측정하지 않은 추정값을 근거로 쓰지 않는다. 결정 당시 사실과 미래 가정을 구분한다.

## 3. 결정 규칙

### Redis 세션 선택

다음을 만족하면 Redis 세션이 적합하다.

- 목표 부하와 tail latency 기준 충족
- 즉시 로그아웃·강제 만료 요구 충족
- Redis 장애의 fail-closed 정책과 사용자 영향 수용
- 개인화 SSE ticket 제거의 복잡성 감소 확인
- Redis HA·관측·용량 운영 가능

### JWT 유지

다음을 만족하면 JWT 유지가 적합하다.

- Redis 세션이 목표 성능 또는 장애 목표를 충족하지 못함
- 로컬 검증의 가용성 이점이 실제 요구사항에 중요함
- Access Token 만료 전 무효화 한계를 수용하거나 denylist 비용을 감수함
- Refresh Rotation과 SSE ticket을 운영할 수 있음
- 향후 독립 리소스 서버 분리 가능성이 구체적임

성능 차이가 작으면 코드·운영 단순성과 보안 통제 명확성을 우선한다.

## 4. 배포 전 공통 준비

- 최종 모드를 환경별 설정에 명시한다.
- 프론트와 백엔드 인증 모드를 같은 배포 단위로 맞춘다.
- 혼합 버전 인스턴스가 같은 트래픽을 처리해도 되는지 확인한다.
- JWT와 세션 상태가 호환되지 않아 재로그인이 발생할 수 있음을 공지한다.
- 로그아웃·계정 정지·입찰·지갑·개인화 SSE smoke test를 준비한다.
- 인증 실패율과 저장소 장애 경보를 배포 전에 구성한다.

## 5. 배포와 롤백

### 개발·검증 환경

환경변수를 바꾸고 테스트 데이터를 초기화해 최종 모드를 검증한다. 이전 모드의 cookie와 브라우저 메모리 상태를 제거해 교차 오염을 막는다.

### 실제 사용자 환경

1. 최종 모드의 백엔드와 프론트를 같은 릴리스로 배포한다.
2. 필요하면 기존 사용자에게 재로그인을 요구한다.
3. 인증 실패율·로그인 성공률·입찰 오류·SSE 재연결을 집중 관찰한다.
4. 문제 발생 시 직전 이미지와 인증 모드로 함께 롤백한다.
5. 롤백 기간 동안 이전 비밀키·Refresh 데이터 또는 Redis namespace를 보존한다.

롤백 기간이 끝난 뒤에만 미선택 구현과 데이터 구조를 제거한다.

## 6. Redis 세션을 선택한 경우 제거

### 백엔드

- JWT·Refresh 패키지와 조건부 구성 제거
- `JwtAuthFilter` 제거
- Refresh API와 cookie factory 제거
- SSE ticket API·provider·filter 제거
- JWT library와 설정 제거
- JWT 전용 계약 분기를 공통 세션 계약으로 단순화

### 프론트

- Access Token memory store 제거
- Bearer header 첨부 제거
- 동시 401 단일 Refresh 로직 제거
- JWT AuthTransport와 SSE ticket transport 제거
- `VITE_AUTH_MODE` 분기 제거

### 데이터·운영

- JWT 비밀키와 관련 secret 제거
- 롤백 기간 후 `authentication` 테이블 제거 migration
- Refresh Token 관련 metric·alert 제거
- Redis session namespace의 TTL·용량·alert를 운영 설정으로 승격

## 7. JWT를 선택한 경우 제거

### 백엔드

- session 인증 패키지와 조건부 구성 제거
- Spring Session Redis 의존성과 설정 제거
- 세션 repository와 사용자별 세션 index 제거
- 세션 CSRF Token API와 필터 제거
- 세션 전용 SSE 연결 종료 연동 제거

Origin·Fetch Metadata 검증이 JWT 요청에도 유효한 방어라면 별도 근거를 남기고 유지한다. 세션 코드를 제거한다는 이유로 공통 보안 검증까지 자동 삭제하지 않는다.

### 프론트

- session AuthTransport 제거
- session cookie용 `credentials` 분기 제거
- CSRF Token 메모리와 header 주입 제거
- cookie 기반 SSE transport 제거
- `VITE_AUTH_MODE` 분기 제거

### 데이터·운영

- 실험용 Redis session namespace 정리
- 세션 저장소 secret·timeout·alert 제거
- JWT Access Token 무효화 정책과 수용 위험을 운영 문서에 확정

## 8. 패키지 제거 가능성 검증

최종 상태에서 다음이 0건이어야 한다.

- 선택하지 않은 패키지의 source와 test
- 선택하지 않은 모드의 환경변수
- 선택하지 않은 구현을 참조하는 import
- 사용하지 않는 endpoint와 API 문서
- 사용하지 않는 프론트 transport와 store
- 실험용 Redis key 또는 불필요한 DB 구조

도메인 코드는 최종 인증 구현을 직접 참조하지 않고 `@CurrentUser` 계약만 사용할 수 있다. 최종 구현이 하나라면 실험용 `AuthenticationStrategy` 추상화도 가치가 없는지 검토해 단순화한다.

## 9. 최종 검증

### 기능

- 로그인·인증 복구·로그아웃
- 계정 정지·강제 만료
- 입찰·지갑·대시보드 보호
- 공개·개인화 SSE
- 알림 재연결과 누락 복구

### 보안

- 인증 우회 0건
- 다른 사용자 세션·토큰 혼동 0건
- 로그아웃·정지 후 잘못 허용된 요청 0건
- secret·Token·Session ID 로그 노출 0건
- 선택한 모드의 CSRF·XSS 위험 대응 확인

### 품질

- 백엔드 전체 테스트
- 프론트 lint·test·build
- 최종 모드 부하 회귀 테스트
- 다중 인스턴스 smoke test
- 배포·롤백 리허설

테스트 소스가 없는 명령은 통과로 표현하지 않고 테스트가 없음을 기록한다.

## 10. 완료 기준

- 하나의 인증 방식만 런타임과 코드에 남는다.
- 최종 결정과 기각 이유가 측정 결과에 연결된다.
- 사용하지 않는 패키지·설정·endpoint·데이터 구조가 제거된다.
- 전체 기능·보안·빌드·부하 회귀 검증이 완료된다.
- 롤백 기간 종료와 비밀정보 폐기 여부가 기록된다.
- README의 기승전결은 최종 구현을 과장하지 않고 당시 비교 과정을 보존한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
