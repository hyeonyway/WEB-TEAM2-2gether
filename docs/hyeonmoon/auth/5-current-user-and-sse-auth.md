# Current User & SSE Ticket Auth Implementation Plan

**Goal:** (1) 다른 도메인이 실제 `JwtAuthFilter` 완성을 기다리지 않고 로그인 유저를 식별할 수 있도록 전역 `CurrentUserProvider`/`@CurrentUser`와 임시 `X-Debug-User-Id` 필터의 시그니처를 먼저 확정해 팀에 공유한다. (2) 브라우저 `EventSource`가 커스텀 헤더를 지정할 수 없어 SSE 요청에 `Authorization` 헤더를 실을 수 없으므로, 짧은 TTL의 1회용 티켓을 발급해 SSE 스트림 인증에 쓴다. 두 인터페이스 다 `global.security` 소유이며 A(김현문)가 구현한다.

**Architecture:**

- `CurrentUserProvider`는 순수 `Integer userId`만 반환하는 얇은 전역 계약이다(로그인 유저 식별처럼 전원 공통 관심사이므로 특정 도메인 소유가 아니다 — `module-interfaces.md` 4절).
- `TicketProvider`도 같은 이유로 `global.security` 소유다. 이후 전역 `SseTicketAuthFilter`가 이 인터페이스에 의존하고, 대시보드(정세호)/알림(임하민)의 SSE 컨트롤러는 티켓을 직접 다루지 않은 채 `@CurrentUser Integer userId`만 사용한다. 경매 목록/상세 SSE(정세호 담당, 이은기는 이벤트 발행만)는 공개 데이터라 티켓을 쓰지 않는다.
- SSE 티켓은 현재 단일 애플리케이션 인스턴스 안의 메모리에 저장한다. 발급
  인스턴스와 소비 인스턴스가 항상 같으므로 Redis 같은 공유 저장소는 도입하지
  않는다. 멀티 인스턴스로 전환할 때만 `TicketProvider` 구현체를 공유 저장소
  기반으로 교체한다.
- **기존 코드와의 관계(중요):** `auction/port/CurrentUserPort.java`가 이미 존재하며 `id`/`nickname`/`seller`/`restricted`를 담은 자체 `CurrentUser` record를 쓴다. 계정 자체는 판매자와 구매자를 구분하지 않고, 이 Port는 `auctionId`도 받지 않아 `seller`를 판정할 수 없다. 따라서 실제 어댑터 구현은 이 문서에서 강행하지 않고 Auction 담당자와 계약을 먼저 조정한다(Task 6).

**Tech Stack:** Spring MVC(`OncePerRequestFilter`, `HandlerMethodArgumentResolver`), JJWT, JDK 동시성 컬렉션, JUnit 5, Mockito

## Global Constraints

- `CurrentUserProvider`/`@CurrentUser`는 `Integer userId`만 다룬다. 닉네임/권한 등 필요한 도메인은 자기 포트를 따로 정의한다(`CurrentUserPort` 참고).
- `global.security`는 다른 도메인의 Entity나 Repository를 참조하지 않는다.
- `X-Debug-User-Id` 헤더 기반 `TestAuthFilter`는 `debug-auth` 프로필을 명시적으로 활성화한 경우에만 JWT가 없는 요청의 fallback으로 사용한다. 기본 프로필과 운영 환경에서는 등록하지 않는다.
- `JwtAuthFilter`는 기본 인증 필터로 등록한다. Authorization 헤더가 없으면 요청을 그대로 통과시키고, 인증이 필요한 컨트롤러에서는 `CurrentUserProvider`가 `UnauthorizedException`을 발생시킨다.
- 티켓은 JWT가 아니다 — 클레임 없는 불투명한 랜덤 문자열이며, 검증 성공 시 즉시 폐기되는 1회용이다. TTL은 30초로 고정한다.
- 티켓 저장소는 `ConcurrentHashMap` 기반 단일 인스턴스 메모리 저장소다. 검증 시
  `remove()`로 티켓을 원자적으로 소비하고, 주기적인 정리 작업으로 만료된 미사용
  티켓을 제거한다.
- 진짜 JWT(Access/Refresh Token)는 어떤 경우에도 쿼리파라미터에 실리지 않는다.
- `SseTicketAuthFilter`는 설정된 SSE 스트림 경로에만 적용되며, 그 외 경로의 `JwtAuthFilter` 동작에는 영향을 주지 않는다.

---

## 지금 바로 팀에 공유할 것 (Task 1~2)

### Task 1: CurrentUserProvider 인터페이스 + 임시 디버그 필터

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/CurrentUserProvider.java`
- Create: `backend/src/main/java/com/dbidding/global/security/CurrentUser.java` (`@CurrentUser` 어노테이션)
- Create: `backend/src/main/java/com/dbidding/global/security/CurrentUserArgumentResolver.java`
- Create: `backend/src/main/java/com/dbidding/global/security/TestAuthFilter.java`
- Create: `backend/src/main/java/com/dbidding/global/security/RequestCurrentUserProvider.java`
- Create: `backend/src/main/java/com/dbidding/global/exception/UnauthorizedException.java` — 아직 존재하지 않음(`global/exception`은 현재 `.gitkeep`뿐)
- Modify: `backend/src/main/java/com/dbidding/global/config/WebConfig.java` — `addArgumentResolvers()` override 추가(현재 `addCorsMappings()`만 있음)
- Test: `backend/src/test/java/com/dbidding/global/security/TestAuthFilterTest.java`
- Test: `backend/src/test/java/com/dbidding/global/security/RequestCurrentUserProviderTest.java`
- Test: `backend/src/test/java/com/dbidding/global/security/CurrentUserArgumentResolverTest.java`
- Test: `backend/src/test/java/com/dbidding/global/security/CurrentUserWebMvcTest.java`
- Test: `backend/src/test/java/com/dbidding/global/security/CurrentUserDefaultProfileWebMvcTest.java`

**Interfaces:**
- Produces: `Integer CurrentUserProvider.getCurrentUserId()` — 토큰/헤더가 없거나 무효면 `UnauthorizedException`
- Produces: `@CurrentUser` 파라미터 어노테이션(컨트롤러에서 `Integer userId`로 주입)

- [x] **Step 1: 인터페이스와 어노테이션 작성**

```java
package com.dbidding.global.security;

public interface CurrentUserProvider {
    Integer getCurrentUserId();
}
```

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
```

이 두 시그니처를 오늘 팀에 우선 공유한다 — B/C/D는 이것만으로 컨트롤러 파라미터에 `@CurrentUser Integer userId`를 쓰는 코드를 바로 작성할 수 있다.

- [x] **Step 2: 임시 디버그 필터 (X-Debug-User-Id)**

```java
@Component
@Profile("debug-auth")
public class TestAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws IOException, ServletException {
        String debugUserId = request.getHeader("X-Debug-User-Id");
        if (debugUserId != null) {
            try {
                int userId = Integer.parseInt(debugUserId);
                if (userId > 0) {
                    request.setAttribute("userId", userId);
                }
            } catch (NumberFormatException ignored) {
                // 잘못된 헤더는 인증 정보가 없는 요청으로 취급한다.
            }
        }
        chain.doFilter(request, response);
    }
}
```

`X-Debug-User-Id`는 양의 `Integer`만 허용한다. 헤더가 없거나 숫자가 아니거나
0 이하이면 attribute를 설정하지 않으며, 인증이 필요한 컨트롤러에서는
`CurrentUserProvider`가 `UnauthorizedException`을 발생시킨다.

- [x] **Step 3: request attribute를 읽는 Provider와 리졸버**

```java
@Component
public class RequestCurrentUserProvider implements CurrentUserProvider {
    private final HttpServletRequest request;

    @Override
    public Integer getCurrentUserId() {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }
}
```

```java
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    private final CurrentUserProvider currentUserProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return currentUserProvider.getCurrentUserId();
    }
}
```

- [x] **Step 4: `UnauthorizedException` + `WebConfig` 등록**

```java
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {
}
```

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 기존 CORS 설정 그대로 유지
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
```

`TestAuthFilter`는 `debug-auth` 프로필에서만 Spring Boot가 자동으로 필터체인에
추가한다(별도 `FilterRegistrationBean` 불필요). 로컬에서 사용할 때는
`SPRING_PROFILES_ACTIVE=debug-auth`를 명시한다. `!prod` 조건은 배포 환경에서
`prod` 프로필 설정이 누락되면 디버그 인증이 활성화될 수 있으므로 사용하지 않는다.

### Task 2: TicketProvider 인터페이스

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/TicketProvider.java`

**Interfaces:**
- Produces: `String TicketProvider.issue(Integer userId)`
- Produces: `Integer TicketProvider.validateAndConsume(String ticket)` — 무효/만료/이미 소비된 티켓이면 `UnauthorizedException`
- Produces: `long TicketProvider.ticketTtlSeconds()` — 발급 응답과 실제 만료 시간이 같은 TTL 정의를 사용하도록 초 단위 값을 제공

- [x] **Step 1: 인터페이스 작성**

```java
package com.dbidding.global.security;

public interface TicketProvider {
    String issue(Integer userId);
    Integer validateAndConsume(String ticket);
    long ticketTtlSeconds();
}
```

이 시그니처도 오늘 팀에 공유한다. 다만 대시보드/알림 컨트롤러가 직접
`TicketProvider`를 주입받지는 않는다. 이후 `SseTicketAuthFilter`가 티켓을 검증해
request attribute에 `userId`를 넣고, 각 컨트롤러는 공통 `@CurrentUser` 계약만
사용한다.

---

## 이후 실제 구현 (Task 3~8)

### Task 3: 실제 JwtAuthFilter와 Current User 연결

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/JwtAuthFilter.java`
- Test: `backend/src/test/java/com/dbidding/global/security/JwtAuthFilterTest.java`

- [x] **Step 1: 유효 토큰이면 request attribute에 userId를 채운다**

```java
@Test
void 유효한_Access_Token이면_userId를_attribute에_저장한다() {
    given(jwtTokenProvider.parseAccess("valid-token")).willReturn(new TokenClaims(1, TokenType.ACCESS));

    filter.doFilterInternal(request, response, chain);

    then(request).should().setAttribute("userId", 1);
}
```

- [x] **Step 2: 토큰이 없으면 request attribute를 채우지 않고 통과시킨다. 잘못된 Bearer 토큰은 401로 처리한다**
- [x] **Step 3: `Authorization: Bearer ...` 헤더에서 토큰 추출 후 `JwtTokenProvider.parseAccess()`(3-login-and-token.md 산출물) 재사용**

이 필터는 기본 인증 흐름에 등록한다. `debug-auth` 프로필에서는 Bearer 토큰이
없는 요청에 한해 `TestAuthFilter`가 사용자 ID를 보완한다. 두 헤더가 함께 오면
검증된 JWT 사용자 ID를 우선하며 debug header가 덮어쓰지 않는다.

### Task 4: 단일 인스턴스용 InMemoryTicketProvider 구현

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/InMemoryTicketProvider.java`
- Test: `backend/src/test/java/com/dbidding/global/security/InMemoryTicketProviderTest.java`

- [x] **Step 1: 발급 테스트**

```java
@Test
void 유저_ID로_티켓을_발급하고_TTL을_설정한다() {
    String ticket = provider.issue(1);

    assertThat(ticket).isNotBlank();
    assertThat(provider.validateAndConsume(ticket)).isEqualTo(1);
}
```

- [x] **Step 2: 1회성 검증 테스트**

```java
@Test
void 티켓_검증에_성공하면_동일_티켓_재사용이_불가능하다() {
    String ticket = provider.issue(1);

    assertThat(provider.validateAndConsume(ticket)).isEqualTo(1);
    assertThatThrownBy(() -> provider.validateAndConsume(ticket))
        .isInstanceOf(UnauthorizedException.class);
}

@Test
void 발급_후_30초가_지나면_티켓을_거절한다() {
    String ticket = provider.issue(1);
    clock.advance(Duration.ofSeconds(31));

    assertThatThrownBy(() -> provider.validateAndConsume(ticket))
        .isInstanceOf(UnauthorizedException.class);
}
```

- [x] **Step 3: 최소 구현**

```java
@Component
public class InMemoryTicketProvider implements TicketProvider {
    private static final Duration TTL = Duration.ofSeconds(30);
    private final ConcurrentMap<String, TicketEntry> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    @Override
    public String issue(Integer userId) {
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new TicketEntry(userId, clock.instant().plus(TTL)));
        return ticket;
    }

    @Override
    public long ticketTtlSeconds() {
        return TTL.toSeconds();
    }

    @Override
    public Integer validateAndConsume(String ticket) {
        TicketEntry entry = tickets.remove(ticket);
        if (entry == null || !clock.instant().isBefore(entry.expiresAt())) {
            throw new UnauthorizedException();
        }
        return entry.userId();
    }

    @Scheduled(fixedDelay = 60_000)
    void removeExpiredTickets() {
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record TicketEntry(Integer userId, Instant expiresAt) {}
}
```

`ConcurrentHashMap.remove()`로 조회와 삭제를 한 번에 처리해 같은 프로세스 안에서
1회성을 보장한다. 미사용 티켓은 검증할 때 만료 여부를 확인하고, 60초 주기의
정리 작업으로 메모리에서도 제거한다. 이 구현은 단일 인스턴스 전용이다. 추후
멀티 인스턴스로 전환하면 `TicketProvider` 인터페이스는 유지하고 공유 저장소
구현체로 교체한다.

### Task 5: 티켓 발급 API + SSE 인증 필터

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/SseTicketController.java`
- Create: `backend/src/main/java/com/dbidding/global/security/SseTicketResponse.java`
- Create: `backend/src/main/java/com/dbidding/global/security/SseTicketAuthFilter.java`

- [x] **Step 1: 발급 엔드포인트**

```java
@RestController
public class SseTicketController {
    private final TicketProvider ticketProvider;

    @PostMapping("/api/sse/tickets")
    public TicketResponse issue(@CurrentUser Integer userId) {
        return new TicketResponse(
            ticketProvider.issue(userId),
            ticketProvider.ticketTtlSeconds()
        );
    }
}
```

기존 `JwtAuthFilter`가 이미 처리한 요청이므로 `@CurrentUser`를 그대로 쓴다 — 새 인증 로직이 필요 없다. 응답의 `expiresInSeconds`는 Provider의 실제 TTL을 사용해 두 값이 따로 변경되는 것을 막는다.

- [x] **Step 2: SSE 경로용 인증 필터**

```java
public class SseTicketAuthFilter extends OncePerRequestFilter {
    private final TicketProvider ticketProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws IOException, ServletException {
        String ticket = request.getParameter("ticket");
        Integer userId = ticketProvider.validateAndConsume(ticket);
        request.setAttribute("userId", userId);
        chain.doFilter(request, response);
    }
}
```

`JwtAuthFilter`가 request attribute에 `userId`를 저장하는 것과 동일한 방식으로 저장하므로, 대시보드/알림 컨트롤러는 `@CurrentUser Integer userId`를 그대로 쓰면 된다 — `TicketProvider`를 직접 호출할 필요가 없다. 이 필터는 `/api/dashboard/stream`, `/api/users/{userId}/auctions/stream`, `/api/users/{userId}/notifications/stream`에만 등록하고 그 외 경로는 기존 `JwtAuthFilter`를 그대로 통과시킨다.

- [x] **Step 3: 통합 테스트**

```java
@Test
void 유효한_티켓으로_SSE_요청하면_현재유저로_인증된다() {
    given(ticketProvider.validateAndConsume("abc")).willReturn(1);

    mockMvc.perform(get("/api/dashboard/stream").param("ticket", "abc"))
        .andExpect(status().isOk());
}
```

### Task 6: `auction.CurrentUserPort` 계약 조정 후 별도 구현

현재 `CurrentUserPort.CurrentUser`의 `seller`는 계정 단위 속성처럼 정의되어
있지만, Dibidding은 판매자 계정과 구매자 계정을 구분하지 않는다. 특정 경매의
판매자 여부는 `auction.sellerId`와 현재 사용자 ID를 비교해야 하며, 경매 생성
시점에는 비교할 경매도 없다.

따라서 #83에서는 실제 어댑터를 구현하지 않는다. Auction 담당자와 다음 내용을
먼저 합의한 뒤 별도 작업으로 진행한다.

- 계정 단위 `seller` 제거
- 경매 생성 가능 여부는 `UserStatus.ACTIVE` 기준으로 판단
- 특정 경매의 소유자 여부는 Auction 안에서 `sellerId`와 현재 사용자 ID 비교
- `active`와 `restricted`처럼 서로 반대되는 중복 boolean은 하나로 정리

### Task 7: 팀 사용 가이드 (오늘 바로 적용 가능)

다른 담당자는 아래 중 하나만 알면 된다.

- **단순히 "누구인지"만 필요하면**: `@CurrentUser Integer userId`를 컨트롤러 파라미터에 쓰고, 로컬 개발 시 요청에 `X-Debug-User-Id: 1` 헤더를 실어 보낸다.
- **프로필까지 필요하면(현재는 auction만 해당)**: 자기 도메인에 `CurrentUserPort` 같은 인터페이스를 정의하고 `@Profile`로 분리한 mock 어댑터를 만들어 개발한다. 실제 구현은 Task 6처럼 나중에 채워 넣는다.
- **SSE 인증이 필요하면(대시보드/알림)**: 컨트롤러는 일반 API와 동일하게
  `@CurrentUser Integer userId`를 사용한다. 브라우저 `EventSource` 연결에 필요한
  티켓 발급·검증은 Task 4~5의 전역 구현이 담당한다.

자기 SSE 컨트롤러에서는 `TicketProvider`를 직접 주입받을 필요가 없다. `SseTicketAuthFilter`가 이미 검증을 마치고 request attribute에 넣어주므로, 기존 컨트롤러와 동일하게 작성하면 된다.

```java
@GetMapping(value = "/api/dashboard/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@CurrentUser Integer userId) {
    SseEmitter emitter = new SseEmitter(0L);
    registry.register(userId, emitter);
    return emitter;
}
```

김현문의 실제 구현(`InMemoryTicketProvider`/`JwtAuthFilter`)이 끝나도
SSE 컨트롤러/서비스 코드는 변경하지 않는다. 인증 필터가 request attribute를
채우는 방식만 디버그 헤더에서 JWT 또는 SSE 티켓으로 교체된다.

### Task 8: 단위 테스트와 커밋

```bash
./gradlew clean test
```

구현은 JWT 필터, 인메모리 티켓 저장소, 티켓 발급 API, SSE 티켓 필터를 각각
독립 커밋으로 나눈다.

## 완료 조건

- `debug-auth` 프로필에서 `@CurrentUser Integer userId`만으로 로그인 유저 식별이 가능하다.
- `TestAuthFilter`는 `X-Debug-User-Id` 헤더가 없으면 아무 attribute도 채우지 않아, 인증이 실제로 필요한 곳에서는 여전히 `UnauthorizedException`이 발생한다.
- 기본 프로필과 운영 환경에서는 `X-Debug-User-Id` 헤더만으로 인증할 수 없다.
- `JwtAuthFilter`는 기본 인증 흐름에 등록되고, `debug-auth` 프로필에서만
  `X-Debug-User-Id` fallback을 허용한다.
- 발급된 티켓은 30초 후 자동 만료되고, 동일 티켓 재사용은 거절된다(1회성).
- 티켓은 단일 인스턴스 메모리에 저장되며, 만료된 미사용 티켓은 주기적으로
  정리된다.
- 진짜 JWT(Access/Refresh Token)는 어떤 요청 URL에도 노출되지 않는다.
- 대시보드/알림 컨트롤러는 `TicketProvider`를 직접 호출하지 않고 `@CurrentUser`만으로 유저를 식별한다.
- `global.security`는 `user`/`auction`의 Entity를 직접 참조하지 않는다.
- `auction.CurrentUserPort` 실제 어댑터는 `seller` 계약을 조정하기 전까지
  구현하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
