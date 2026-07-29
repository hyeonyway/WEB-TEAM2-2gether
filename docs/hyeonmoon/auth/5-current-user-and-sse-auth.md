# Current User & SSE Ticket Auth Implementation Plan

**Goal:** (1) 다른 도메인이 실제 `JwtAuthFilter` 완성을 기다리지 않고 로그인 유저를 식별할 수 있도록 전역 `CurrentUserProvider`/`@CurrentUser`와 임시 `X-Debug-User-Id` 필터의 시그니처를 먼저 확정해 팀에 공유한다. (2) 브라우저 `EventSource`가 커스텀 헤더를 지정할 수 없어 SSE 요청에 `Authorization` 헤더를 실을 수 없으므로, 짧은 TTL의 1회용 티켓을 발급해 SSE 스트림 인증에 쓴다. 두 인터페이스 다 `global.security` 소유이며 A(김현문)가 구현한다.

**Architecture:**

- `CurrentUserProvider`는 순수 `Integer userId`만 반환하는 얇은 전역 계약이다(로그인 유저 식별처럼 전원 공통 관심사이므로 특정 도메인 소유가 아니다 — `module-interfaces.md` 4절).
- `TicketProvider`도 같은 이유로 `global.security` 소유다. 이후 전역 `SseTicketAuthFilter`가 이 인터페이스에 의존하고, 대시보드(정세호)/알림(임하민)의 SSE 컨트롤러는 티켓을 직접 다루지 않은 채 `@CurrentUser Integer userId`만 사용한다. 경매 목록/상세 SSE(정세호 담당, 이은기는 이벤트 발행만)는 공개 데이터라 티켓을 쓰지 않는다.
- **기존 코드와의 관계(중요):** `auction/port/CurrentUserPort.java`가 이미 존재하며 `id`/`nickname`/`seller`/`restricted`를 담은 자체 `CurrentUser` record를 쓴다(`auction/adapter/MockCurrentUserAdapter.java`가 `@Profile("auction-mock")`으로 고정값 반환 중). `CurrentUserProvider`와 경쟁하는 게 아니라 다른 층위다 — `CurrentUserProvider`는 "누구인지(userId)"만, `CurrentUserPort`처럼 프로필까지 필요한 도메인은 자기 인터페이스를 따로 정의해 쓴다(`module-interfaces.md`의 "쓰는 쪽이 정의" 원칙). 실제 JWT 완성 후 `CurrentUserProvider.getCurrentUserId()` + `UserRepository` 조회를 조합해 `CurrentUserPort`의 실제 어댑터로 `MockCurrentUserAdapter`를 교체한다(Task 6).

**Tech Stack:** Spring MVC(`OncePerRequestFilter`, `HandlerMethodArgumentResolver`), JJWT, Spring Data Redis, JUnit 5, Mockito

## Global Constraints

- `CurrentUserProvider`/`@CurrentUser`는 `Integer userId`만 다룬다. 닉네임/권한 등 필요한 도메인은 자기 포트를 따로 정의한다(`CurrentUserPort` 참고).
- `global.security`는 다른 도메인의 Entity나 Repository를 참조하지 않는다.
- `X-Debug-User-Id` 헤더 기반 `TestAuthFilter`는 `debug-auth` 프로필을 명시적으로 활성화한 경우에만 사용한다. 기본 프로필에서는 등록하지 않으며, 실제 `JwtAuthFilter` 전역 적용 시 제거한다.
- `CurrentUserArgumentResolver`는 지금 등록해 `TestAuthFilter`와 함께 사용한다. `JwtAuthFilter`만 구현 후 인증 통합일까지 전역 등록을 미룬다.
- 티켓은 JWT가 아니다 — 클레임 없는 불투명한 랜덤 문자열이며, 검증 성공 시 즉시 폐기되는 1회용이다. TTL은 30초로 고정한다.
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

- [x] **Step 1: 인터페이스 작성**

```java
package com.dbidding.global.security;

public interface TicketProvider {
    String issue(Integer userId);
    Integer validateAndConsume(String ticket);
}
```

이 시그니처도 오늘 팀에 공유한다. 다만 대시보드/알림 컨트롤러가 직접
`TicketProvider`를 주입받지는 않는다. 이후 `SseTicketAuthFilter`가 티켓을 검증해
request attribute에 `userId`를 넣고, 각 컨트롤러는 공통 `@CurrentUser` 계약만
사용한다.

---

## 이후 실제 구현 (Task 3~8)

### Task 3: 실제 JwtAuthFilter (구현은 지금, 전역 적용은 인증 통합일)

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/JwtAuthFilter.java`
- Test: `backend/src/test/java/com/dbidding/global/security/JwtAuthFilterTest.java`

- [ ] **Step 1: 유효 토큰이면 request attribute에 userId를 채운다**

```java
@Test
void 유효한_Access_Token이면_userId를_attribute에_저장한다() {
    given(jwtTokenProvider.parseAccess("valid-token")).willReturn(new TokenClaims(1, TokenType.ACCESS));

    filter.doFilterInternal(request, response, chain);

    then(request).should().setAttribute("userId", 1);
}
```

- [ ] **Step 2: 토큰 없거나 무효면 request attribute를 채우지 않는다**(거절은 `CurrentUserProvider` 쪽에서 함, 필터는 파싱만 담당)
- [ ] **Step 3: `Authorization: Bearer ...` 헤더에서 토큰 추출 후 `JwtTokenProvider.parseAccess()`(3-login-and-token.md 산출물) 재사용**

이 필터는 **인증 통합일 전까지 프로필에 등록하지 않는다** — `TestAuthFilter`만 활성 상태를 유지한다. 완성해두는 이유는 인증 통합일에 스위치만 바꾸면 되도록 미리 준비하기 위함이다.

### Task 4: Redis 기반 TicketProvider 구현

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/RedisTicketProvider.java`
- Test: `backend/src/test/java/com/dbidding/global/security/RedisTicketProviderTest.java`

- [ ] **Step 1: 발급 테스트**

```java
@Test
void 유저_ID로_티켓을_발급하고_TTL을_설정한다() {
    String ticket = provider.issue(1);

    then(redisTemplate.opsForValue()).should()
        .set(eq("sse:ticket:" + ticket), eq("1"), eq(Duration.ofSeconds(30)));
}
```

- [ ] **Step 2: 1회성 검증 테스트**

```java
@Test
void 티켓_검증에_성공하면_동일_티켓_재사용이_불가능하다() {
    given(redisTemplate.opsForValue().getAndDelete("sse:ticket:abc")).willReturn("1");

    Integer userId = provider.validateAndConsume("abc");

    assertThat(userId).isEqualTo(1);
    // 두 번째 호출은 getAndDelete가 null을 반환하므로 별도 테스트로 확인
}

@Test
void 만료되었거나_이미_소비된_티켓은_거절한다() {
    given(redisTemplate.opsForValue().getAndDelete("sse:ticket:abc")).willReturn(null);

    assertThatThrownBy(() -> provider.validateAndConsume("abc"))
        .isInstanceOf(UnauthorizedException.class);
}
```

- [ ] **Step 3: 최소 구현**

```java
@Component
public class RedisTicketProvider implements TicketProvider {
    private static final Duration TTL = Duration.ofSeconds(30);
    private final StringRedisTemplate redisTemplate;

    @Override
    public String issue(Integer userId) {
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(ticket), String.valueOf(userId), TTL);
        return ticket;
    }

    @Override
    public Integer validateAndConsume(String ticket) {
        String userId = redisTemplate.opsForValue().getAndDelete(key(ticket));
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return Integer.valueOf(userId);
    }

    private String key(String ticket) {
        return "sse:ticket:" + ticket;
    }
}
```

`getAndDelete`(Redis 6.2+ `GETDEL`)로 조회와 삭제를 한 번에 처리해 1회성을 보장한다. 사용 중인 Redis가 6.2 미만이면 동일 동작을 하는 Lua 스크립트(`GET` 후 `DEL`을 원자적으로 실행)로 대체한다.

### Task 5: 티켓 발급 API + SSE 인증 필터

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/TicketController.java`
- Create: `backend/src/main/java/com/dbidding/global/security/SseTicketAuthFilter.java`

- [ ] **Step 1: 발급 엔드포인트**

```java
@RestController
public class TicketController {
    private final TicketProvider ticketProvider;

    @PostMapping("/api/sse/tickets")
    public TicketResponse issue(@CurrentUser Integer userId) {
        return new TicketResponse(ticketProvider.issue(userId), 30);
    }
}
```

기존 `JwtAuthFilter`가 이미 처리한 요청이므로 `@CurrentUser`를 그대로 쓴다 — 새 인증 로직이 필요 없다.

- [ ] **Step 2: SSE 경로용 인증 필터**

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

- [ ] **Step 3: 통합 테스트**

```java
@Test
void 유효한_티켓으로_SSE_요청하면_현재유저로_인증된다() {
    given(ticketProvider.validateAndConsume("abc")).willReturn(1);

    mockMvc.perform(get("/api/dashboard/stream").param("ticket", "abc"))
        .andExpect(status().isOk());
}
```

### Task 6: `auction.CurrentUserPort` 실제 어댑터로 교체

**Files:**
- Create: `backend/src/main/java/com/dbidding/user/adapter/CurrentUserPortAdapter.java` (또는 `auth` 패키지 — User 조회가 필요하므로 `user` 소유가 자연스러움)
- Modify: `backend/src/main/java/com/dbidding/auction/adapter/MockCurrentUserAdapter.java` — `@Profile("auction-mock")`을 유지하되 실제 어댑터에 `@Profile("!auction-mock")` 부여로 전환

- [ ] **Step 1: `CurrentUserProvider` + `UserRepository` 조합 테스트**

```java
@Test
void 실제_유저정보로_CurrentUser를_구성한다() {
    given(currentUserProvider.getCurrentUserId()).willReturn(1);
    given(userRepository.findById(1)).willReturn(Optional.of(user)); // seller=true, status=ACTIVE

    CurrentUserPort.CurrentUser result = adapter.currentUser();

    assertThat(result.id()).isEqualTo(1);
    assertThat(result.seller()).isTrue();
    assertThat(result.restricted()).isFalse();
}
```

- [ ] **Step 2: 최소 구현**

```java
@Component
@Profile("!auction-mock")
public class CurrentUserPortAdapter implements CurrentUserPort {
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    @Override
    public CurrentUser currentUser() {
        Integer userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
        return new CurrentUser(
            user.getId(), user.getNickname(),
            user.getRole() == UserRole.SELLER,
            user.getStatus() == UserStatus.SUSPENDED
        );
    }
}
```

`seller`/`restricted` 판정 기준(어떤 role/status 값을 매핑할지)은 실제 `UserRole`/`UserStatus` enum이 확정되는 대로 이은기와 맞춘다 — 지금은 스키마 초안 기준 추정값이다.

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

김현문의 실제 구현(`RedisTicketProvider`/`CurrentUserPortAdapter`/`JwtAuthFilter`)이
끝나도 컨트롤러/서비스 코드는 변경하지 않는다. 인증 필터가 request attribute를
채우는 방식만 디버그 헤더에서 JWT 또는 SSE 티켓으로 교체된다.

### Task 8: 단위 테스트와 커밋

```bash
./gradlew test --tests com.dbidding.global.security.*
git add backend/src/main/java/com/dbidding/global/security backend/src/test/java/com/dbidding/global/security \
  backend/src/main/java/com/dbidding/global/exception backend/src/main/java/com/dbidding/global/config \
  backend/src/main/java/com/dbidding/user
git commit -m "feat: 전역 CurrentUserProvider와 SSE 티켓 인증 추가"
```

## 완료 조건

- `debug-auth` 프로필에서 `@CurrentUser Integer userId`만으로 로그인 유저 식별이 가능하다.
- `TestAuthFilter`는 `X-Debug-User-Id` 헤더가 없으면 아무 attribute도 채우지 않아, 인증이 실제로 필요한 곳에서는 여전히 `UnauthorizedException`이 발생한다.
- 기본 프로필과 운영 환경에서는 `X-Debug-User-Id` 헤더만으로 인증할 수 없다.
- `JwtAuthFilter`는 구현이 끝나 있으나 인증 통합일 전까지 전역 필터체인에 등록되지 않는다.
- 발급된 티켓은 30초 후 자동 만료되고, 동일 티켓 재사용은 거절된다(1회성).
- 진짜 JWT(Access/Refresh Token)는 어떤 요청 URL에도 노출되지 않는다.
- 대시보드/알림 컨트롤러는 `TicketProvider`를 직접 호출하지 않고 `@CurrentUser`만으로 유저를 식별한다.
- `auction.CurrentUserPort`의 실제 어댑터는 `MockCurrentUserAdapter`와 동일한 인터페이스를 만족하며 교체 시 `auction` 쪽 코드 변경이 없다.
- `global.security`는 `user`/`auction`의 Entity를 직접 참조하지 않는다(Task 6의 조합 로직은 `user` 패키지에 둔다).
- `MockCurrentUserAdapter`에서 실제 구현으로 교체하거나 SSE 티켓 필터를 연결할 때 호출부 코드 변경이 없다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
