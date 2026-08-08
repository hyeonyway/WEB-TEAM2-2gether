# 이슈 254 — 세션 SSE 연결 종료 도입 후 인증 설정 테스트 실패

담당: 임하민. 이슈: [#254](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/254)
(브랜치 `fix/254-session-sse-registry-bean-missing`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, 이번 작업
(`account`/`global.security` 패키지 테스트 코드만 수정)은 사용자가 채팅으로 "254 진행해"라고
명시적으로 지시해서 진행한다.

## 원인 1 — `SessionSseConnectionRegistry` 빈 누락

`SessionAuthConfiguration`의 `sessionAuthenticationStrategy` 빈이 `SessionSseConnectionRegistry`를
새 의존성으로 받는데, `JwtAuthenticationConfigurationTest`(`ApplicationContextRunner`)와
`SessionAuthenticationWebMvcTest`(`@WebMvcTest` + `@Import`)는 이 빈을 등록하지 않아서
`NoSuchBeanDefinitionException`으로 컨텍스트 로드가 실패했다.

## 원인 2 — (진단 당시엔 몰랐던) `app.auth.session-enabled` 필수화

원인 1을 고친 뒤 `SessionAuthenticationWebMvcTest`를 다시 돌려보니 별도 에러
(`AuthenticationModeProperties`의 `IllegalArgumentException: Session authentication requires
app.auth.session-enabled=true`)가 새로 나타났다. `AuthenticationModeProperties`가
`mode=SESSION`일 때 `session-enabled=true`를 명시적으로 요구하도록 가드가 추가됐는데
(`ef9f1b5 fix: 세션 인증 설정과 복구 상태 보완`), 이 테스트는 `app.auth.mode=session`만
설정하고 `app.auth.session-enabled`는 설정하지 않아 실패했다. 이슈 제목과는 별개 원인이지만
같은 테스트 클래스의 같은 실패 증상(세션 모드 설정이 최신 프로덕션 요구사항을 못 따라감)이라
이번 작업에서 같이 고쳤다.

## 수정

- `JwtAuthenticationConfigurationTest`: 공용 `contextRunner`에
  `.withBean(SessionSseConnectionRegistry.class, SessionSseConnectionRegistry::new)` 추가
- `SessionAuthenticationWebMvcTest`:
  - `@Import`에 `SessionSseConnectionRegistry.class` 추가
  - `@WebMvcTest(properties = {...})`에 `"app.auth.session-enabled=true"` 추가

## 결과

- `JwtAuthenticationConfigurationTest`(3), `SessionAuthenticationWebMvcTest`(3) 전부 통과
- 전체 스위트 실행 결과 남은 실패는 [#255](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/255)
  (`StatisticAggregationMySqlIntegrationTest`, 4건)뿐 — 이번 변경으로 인한 회귀 없음

## 커밋 이력

1. `fix: 세션 SSE 레지스트리 빈 누락과 session-enabled 설정 누락으로 실패하던 인증 테스트 수정`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
