# 로깅 인프라와 Slack 경고 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그 레벨별 출력 대상(콘솔/파일/Slack)을 구성하고, WARN 이상을 Slack 웹훅으로 실시간 알림한다. **이번 범위는 인프라(appender·설정)까지다 — 비즈니스 코드에 `log.debug/info/warn/error` 호출을 새로 추가하는 작업은 포함하지 않는다.**

**Architecture:** Logback(`logback-spring.xml`) appender로 전체 로그와 WARN 이상 로그를 별도 파일에 저장하고, WARN 이상을 콘솔과 Slack에도 전송한다.

```text
DEBUG/INFO/WARN/ERROR → FILE      (logs/app.log, 로컬 디스크, 롤링, 7일 보관)
WARN/ERROR            → WARN_FILE (logs/warn.log, 로컬 디스크, 롤링, 7일 보관)
WARN/ERROR            → CONSOLE   (표준출력)
WARN/ERROR            → SLACK     (AsyncAppender로 감싼 커스텀 웹훅 appender, 레이트리밋 포함)
```

저장소는 S3가 아니라 로컬 디스크로 정한다(근거는 아래 "저장소: 로컬 vs S3" 절). Slack 웹훅
appender는 HTTP 호출이 로깅 스레드(=요청 처리 스레드)를 막지 않도록 반드시 `AsyncAppender`로
감싼다.

**Tech Stack:** Java 21, Spring Boot 4.1(Logback 내장), JDK `java.net.http.HttpClient`(추가
라이브러리 없이 Slack Incoming Webhook 호출)

## 저장소: 로컬 vs S3

로컬 디스크로 정한다. 이유는 "작은 프로젝트라서"가 아니라, 롤링 파일을 S3에 올리는 방식 자체가
멀티 인스턴스 문제를 실제로는 잘 풀어주지 못하기 때문이다.

- 오늘 쓰이고 있는(아직 롤링 안 된) 파일은 어차피 로컬에만 있다. S3엔 롤링된 과거 파일만
  올라가므로 장애 발생 그 순간의 로그는 S3로 봐도 못 본다 — 최소 하루 지연.
- 인스턴스가 여러 대로 늘어났을 때 실제로 필요한 건 "S3 업로드"가 아니라 **실시간 로그 수집**
  (CloudWatch Logs agent, Fluent Bit 등)이다. 이 프로젝트는 이미 `upload.config.S3Config`/
  `upload.service.S3PresignedUrlProvider`에서 `DefaultCredentialsProvider`(인스턴스 IAM
  역할) 패턴을 쓰고 있으므로, 나중에 멀티 인스턴스로 갈 때는 이 로컬 롤링 파일 위에 CloudWatch
  Logs agent를 사이드카로 붙이는 쪽을 권장한다 — 앱 코드 변경 없이 실시간 스트리밍·검색·보관기간
  설정이 다 된다.
- 지금 S3 업로드 코드를 미리 짜면(실패 재시도, 디스크 꽉 찬 상태에서 업로드 전인 경우 등) 복잡도만
  늘고 정작 원하는 실시간성은 못 얻는다.
- `app.log`와 `warn.log`는 각각 현재 기록 중인 활성 파일이다. 날짜가 바뀌거나 파일이 100MB에
  도달하면 `app.YYYY-MM-DD.N.log.gz` 및 `warn.YYYY-MM-DD.N.log.gz`로 롤링되고, 롤링된 파일은
  `maxHistory=7`에 따라 자동 삭제된다. 활성 파일은 현재 로그를 기록해야 하므로 삭제 대상이 아니다.

## Slack 메시지 포맷 — Workflow Builder 웹훅 기준

**Workflow Builder의 "웹훅" 트리거는 classic Incoming Webhook과 페이로드 형식이 다르다.**
`attachments`/`blocks` 같은 중첩 JSON을 못 받는다 — 트리거를 만들 때 미리 정의해둔 **평평한
(flat) 변수 목록**만 최상위 키로 받고, 실제 메시지 문구·이모지 배치는 Slack UI의 "메시지 보내기"
단계에서 편집기의 **변수 삽입 버튼/드롭다운**으로 조립한다. 아래 문서에서 쓰는 `{{변수명}}`
표기는 설명용 표기일 뿐, 실제 편집기에 저 문자열을 그대로 타이핑하는 게 아니다 — 타이핑하면
그냥 리터럴 텍스트로 남는다(변수는 반드시 삽입 버튼으로 넣어야 함). 색상바(`attachments.color`)도
Workflow Builder 메시지 단계엔 없다 — 레벨 구분은 이모지 + 굵은 글씨로 대신한다.

**코드블럭은 포기하고 일반 텍스트로 보낸다.** 처음엔 스택트레이스를 코드블럭처럼 보이게
하려고 값 자체에 백틱(`` ``` ``)을 미리 포함시켜 보내는 방법을 시도했는데, 실제로 붙여보니
안 먹혔다 — Workflow Builder의 "메시지 보내기" 단계는 변수 값을 `rich_text`로 그대로 넣고,
`rich_text`는 mrkdwn 텍스트와 달리 렌더링 시점에 백틱을 코드블럭으로 재해석하지 않는다. 그
결과 백틱이 리터럴 문자로 그대로 노출됐다(실제 테스트로 확인). 그래서 코드블럭 서식은
포기하고, `stack_excerpt`는 그냥 일반 텍스트로 보낸다.

### 1) 웹훅 트리거에 만들 변수 (전부 Text 타입)

| 변수명 | 예시 값 |
|---|---|
| `level_emoji` | `🔴` (ERROR) / `🟡` (WARN) — Java 쪽에서 레벨 보고 미리 계산해서 넣는다. 워크플로우 안에서 조건 분기 안 써도 되게 하려는 의도. |
| `level` | `ERROR` |
| `service` | `dbidding` (`spring.application.name`) |
| `environment` | `prod` (활성 프로필) |
| `logger` | `com.dbidding.wallet.service.WalletService` |
| `thread` | `http-nio-8080-exec-3` |
| `timestamp` | `2026-08-09 14:32:10 KST` |
| `message` | `지갑 잔액 상태가 올바르지 않습니다.` |
| `exception_type` | `InvalidWalletBalanceException` |
| `stack_excerpt` | 스택트레이스 앞부분(코드펜스 없는 순수 텍스트, 아래 2번 참고) |

전부 Text 타입으로 만든다(워크플로우 웹훅 변수는 Text/Number/Date/Boolean/Slack 사용자·채널
정도만 지원, 중첩 객체·배열 타입 없음). Text 변수 값 길이 제한이 있으니(현재 기준 대략
1,500자 내외 — 실제 값은 Slack 쪽이 바뀔 수 있어 워크플로우 저장 시 에러 나면 그 한도에 맞춰
`stack_excerpt`를 더 짧게 자른다) `stack_excerpt`는 appender가 미리 짧게 자른 뒤 보낸다.

### 2) appender가 실제로 POST하는 JSON (변수명과 키를 그대로 맞춘다)

`stack_excerpt`는 `ThrowableProxyUtil.asString(...)` 결과(첫 줄이 이미
`예외타입: 메시지` 형태)를 앞부분만 잘라 보낸다. 코드펜스는 넣지 않는다.

```json
{
  "level_emoji": "🔴",
  "level": "ERROR",
  "service": "dbidding",
  "environment": "prod",
  "logger": "com.dbidding.wallet.service.WalletService",
  "thread": "http-nio-8080-exec-3",
  "timestamp": "2026-08-09 14:32:10 KST",
  "message": "지갑 잔액 상태가 올바르지 않습니다.",
  "exception_type": "InvalidWalletBalanceException",
  "stack_excerpt": "InvalidWalletBalanceException: 지갑 잔액 상태가 올바르지 않습니다.\n  at com.dbidding.wallet.service.WalletService.validateFrozenBalance(...)"
}
```

### 3) 워크플로우 "메시지 보내기" 단계에 넣을 내용

편집기에서 별도 서식 없이, **변수 삽입 버튼으로 아래 순서대로 변수만 끼워 넣는다**(문자열은
어떻게 조립되는지 설명용 표기일 뿐, 편집기에 직접 타이핑하는 게 아니다).

```text
[level_emoji 삽입] [level 삽입] · [service 삽입] · [environment 삽입]
Logger: [logger 삽입]   Time: [timestamp 삽입]

Message: [message 삽입]

*Exception:* [stack_excerpt 삽입]
```

`exception_type`은 이 템플릿에선 별도로 안 쓴다 — `stack_excerpt` 첫 줄에 이미 예외 타입과
메시지가 포함돼 있다(`ThrowableProxyUtil.asString`의 기본 포맷). 필요하면 예외 타입만 따로
보고 싶을 때를 위해 변수 자체는 계속 남겨둔다.

채널은 워크플로우 만들 때 하나로 고정한다(작은 팀 규모에서 채널 분리는 과함, 레벨 구분은
`level_emoji`로 충분). 색상바나 진짜 코드블럭이 꼭 필요하면 Workflow Builder 대신 classic
Incoming Webhook(Slack App 쪽 "Incoming Webhooks" 기능)을 써야 한다 — 이번 문서는 팀이 이미
Workflow Builder로 정하기로 한 것을 전제로 위 방식으로 진행한다.

## 레이트리밋·중복 억제

장애 한 번에 같은 예외가 수백~수천 건 찍힐 수 있다. 그대로 다 Slack에 보내면 스팸이 되고
Slack 웹훅 자체 레이트리밋(대략 초당 1건)에도 걸린다. `(logger, 예외 클래스)` 키로 슬라이딩
윈도우를 둔다 — 예: 60초당 같은 키 최대 3건 전송, 초과분은 카운트만 누적했다가 윈도우가 끝나면
"최근 60초간 N건 억제됨" 요약 메시지 1건을 보낸다.

## Global Constraints

- 이번 범위는 로깅 인프라(appender·설정)로 한정한다. 비즈니스 코드에 새 로그 호출을 추가하지
  않는다 — 그건 각 도메인 담당자가 필요한 지점에 이후 채워 넣는다.
- Slack 웹훅 URL은 `application.yml`에 값으로 박지 않는다. 환경변수(`SLACK_LOG_WEBHOOK_URL`)로
  주입하고, 비어 있으면 Slack appender가 아무 것도 안 보내고 조용히 스킵한다(로컬 개발 환경에서
  웹훅 설정 없이도 앱이 정상 기동해야 한다).
- Slack 웹훅 HTTP 호출은 로깅을 호출한 스레드(=대부분 실제 요청 처리 스레드)를 절대 막지 않는다
  — 반드시 `AsyncAppender`로 감싸고, HTTP 호출에는 짧은 타임아웃을 둔다.
- Slack appender 내부에서 발생하는 예외(네트워크 오류 등)가 애플리케이션 로직으로 전파되면 안
  된다 — appender 안에서 전부 잡아서 삼킨다.
- 파일 보관 기간(7일)은 Logback `maxHistory` 설정으로만 처리한다 — 별도 정리 배치/스케줄러를
  새로 만들지 않는다.
- 기존 `upload.config.S3Config`/`S3PresignedUrlProvider`의 자격 증명 패턴
  (`DefaultCredentialsProvider`, 인스턴스 IAM 역할)은 이번 작업에서 사용하지 않는다(로컬 저장
  결정에 따라 S3 접근 자체가 이번 범위에 없음). 나중에 멀티 인스턴스로 확장할 때 별도 문서에서
  다룬다.

---

### Task 1: 콘솔·파일 appender를 구성한다

**Files:**
- Create: `backend/src/main/resources/logback-spring.xml`
- Modify: `backend/.gitignore` (로그 디렉터리 커밋 방지)

**Interfaces:**
- Produces: `logs/app.log`/`logs/warn.log`(현재 파일), `logs/app.YYYY-MM-DD.N.log.gz`/
  `logs/warn.YYYY-MM-DD.N.log.gz`(롤링된 과거 파일)
- Preserves: 기존 Spring Boot 기본 로깅 동작과 호환(별도 `logging.*` 프로퍼티 충돌 없음)

- [x] **Step 1: logback-spring.xml에 CONSOLE·FILE·WARN_FILE appender를 작성한다**

CONSOLE과 WARN_FILE은 `ThresholdFilter(WARN)`를 둔다. FILE은 필터 없이(DEBUG부터), WARN_FILE은
WARN 이상만 `SizeAndTimeBasedRollingPolicy`로 저장하며 각각 `maxFileSize=100MB`, `maxHistory=7`,
`totalSizeCap=2GB`를 준다. `root level="DEBUG"`에 세 appender를 붙인다. WARN_FILE은 ERROR도
함께 기록하므로 별도 `error.log`는 만들지 않는다.

- [x] **Step 2: 로그 디렉터리를 gitignore에 추가한다**

`backend/.gitignore`에 `logs/`를 추가한다(로컬 실행 시 생성되는 로그 파일이 커밋되지 않게).

- [x] **Step 3: 로컬에서 동작을 확인한다**

```bash
cd backend
./gradlew bootRun
```

임의로 로그를 하나 찍어보는 임시 테스트 컨트롤러 없이, 기존 기동 로그(Spring Boot 배너, WARN
레벨 이상 로그)만으로 콘솔에는 WARN 이상만 보이는지, `logs/app.log`에는 기동 시 발생하는 DEBUG/
INFO 로그도 함께 쌓이는지, `logs/warn.log`에는 WARN/ERROR만 쌓이는지 확인한다.

- [x] **Step 4: 커밋한다** (`380466b`)

```bash
git add backend/src/main/resources/logback-spring.xml backend/.gitignore
git commit -m "feat: 로그 콘솔·파일 appender 구성(WARN 콘솔, 전체 레벨 파일 7일 보관)"
```

### Task 1-1: WARN 전용 파일 appender를 추가한다

**Files:**
- Modify: `backend/src/main/resources/logback-spring.xml`

**Interfaces:**
- Produces: `logs/warn.log`(현재 파일), `logs/warn.YYYY-MM-DD.N.log.gz`(롤링된 과거 파일)
- Preserves: DEBUG/INFO는 `app.log`에만 기록하고, WARN/ERROR는 기존 CONSOLE·SLACK 출력도 유지한다.

- [x] **Step 1: WARN_FILE appender를 작성한다**

`RollingFileAppender`에 `ThresholdFilter(WARN)`를 적용한다. `file`은 `logs/warn.log`,
`fileNamePattern`은 `logs/warn.%d{yyyy-MM-dd}.%i.log.gz`로 둔다. 전체 파일과 동일하게
`maxFileSize=100MB`, `maxHistory=7`, `totalSizeCap=2GB`를 적용하고 root logger에 연결한다.

- [x] **Step 2: 실제 서버 기동으로 기록 범위를 검증한다**

Redis가 없는 로컬 환경에서 `/actuator/health`를 호출해 WARN 예외를 발생시킨다. `warn.log`에
`DataRedisReactiveHealthIndicator` WARN이 기록되고 DEBUG/INFO가 없으며, `app.log`에는 INFO가
기록되는 것을 확인한다.

- [x] **Step 3: 커밋한다**

```bash
git add backend/src/main/resources/logback-spring.xml \
  docs/hyeonmoon/global/1-logging-slack-alerting.md
git commit -m "feat: WARN 이상 전용 파일 로그 추가"
```

### Task 2: Slack 웹훅 appender를 추가한다

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/logging/SlackWebhookAppender.java`
- Create: `backend/src/test/java/com/dbidding/global/logging/SlackWebhookAppenderTest.java`
- Modify: `backend/src/main/resources/logback-spring.xml`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: `SlackWebhookAppender extends AppenderBase<ILoggingEvent>`(webhook URL, 레이트리밋
  윈도우/최대 건수를 setter로 받는 Logback appender 프로퍼티)
- Consumes: `java.net.http.HttpClient`(POST, 짧은 타임아웃)
- Preserves: Task 1의 CONSOLE·FILE appender 동작

- [x] **Step 1: 레이트리밋 로직 단위 테스트를 먼저 작성한다**

`(logger, 예외 클래스)` 키로 60초 윈도우당 최대 3건만 "전송 대상"으로 판단하고, 초과분은
억제 카운터만 올리는 순수 로직을 검증한다(HTTP 호출과 분리해서 테스트 가능하게 별도 클래스로
뺀다 — 예: `SlackAlertRateLimiter`).

- [x] **Step 2: 테스트가 클래스 부재로 실패하는지 확인한다**

```bash
cd backend
./gradlew test --tests com.dbidding.global.logging.SlackWebhookAppenderTest
```

- [x] **Step 3: SlackWebhookAppender와 레이트리미터를 구현한다**

`append(ILoggingEvent event)`에서: 레이트리미터로 전송 여부 판단 → 억제된 경우 카운트만 증가 →
전송 대상이면 위 "Slack 메시지 포맷" 절의 JSON을 만들어 `HttpClient`로 POST(타임아웃 2~3초) →
모든 예외를 잡아 로깅 프레임워크로 전파하지 않는다. 윈도우가 끝날 때 억제된 건수가 있으면 요약
메시지 1건을 추가로 보낸다.

webhook URL은 `<springProperty scope="context" name="slackWebhookUrl"
source="logging.slack.webhook-url"/>`로 `application.yml`에서 받아오고, 비어 있으면
`append()`가 아무 것도 안 하고 반환한다.

- [x] **Step 4: logback-spring.xml에 AsyncAppender로 감싸서 연결한다**

```xml
<appender name="SLACK_ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <discardingThreshold>0</discardingThreshold>
    <queueSize>500</queueSize>
    <neverBlock>true</neverBlock>
    <appender-ref ref="SLACK"/>
</appender>
<appender name="SLACK" class="com.dbidding.global.logging.SlackWebhookAppender">
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter"><level>WARN</level></filter>
</appender>
```

`root`에 `SLACK_ASYNC`를 추가한다.

- [x] **Step 5: application.yml에 웹훅 설정을 추가한다**

```yaml
logging:
  slack:
    webhook-url: ${SLACK_LOG_WEBHOOK_URL:}
```

기본값을 빈 문자열로 둬서 로컬 개발 환경에서 설정 없이도 앱이 정상 기동하는지 확인한다.

- [x] **Step 6: 테스트를 통과시키고 커밋한다** (`fe918d4`)

```bash
./gradlew test --tests com.dbidding.global.logging.SlackWebhookAppenderTest
git add backend/src/main/java/com/dbidding/global/logging \
  backend/src/test/java/com/dbidding/global/logging \
  backend/src/main/resources/logback-spring.xml \
  backend/src/main/resources/application.yml
git commit -m "feat: WARN 이상 로그를 Slack 웹훅으로 비동기 전송(레이트리밋 포함)"
```

### Task 3: 전체 검증과 문서 정리

**Files:**
- Modify: `docs/hyeonmoon/global/README.md`

- [ ] **Step 1: 실제 Slack 채널로 테스트 웹훅을 확인한다**

서버 Docker 환경에 `SLACK_LOG_WEBHOOK_URL`을 주입한 뒤 수행한다. 웹훅 URL 값은 저장소와 문서에 기록하지 않는다.

임시 웹훅 URL을 로컬 환경변수로 설정하고 WARN/ERROR 로그를 하나씩 발생시켜, 콘솔·파일·Slack
세 군데 모두 정상 도달하는지 확인한다. 같은 예외를 반복 발생시켜 레이트리밋(60초당 3건 제한)이
동작하는지도 확인한다.

- [x] **Step 2: 전체 백엔드 테스트를 실행한다**

```bash
cd backend
./gradlew clean test
```

결과: `./gradlew clean test`에서 127개 테스트가 실패·오류 없이 통과했다.

- [x] **Step 3: global 문서 README를 갱신한다**

`docs/hyeonmoon/global/README.md`의 구현 단계 목록에 이번 문서를 추가한다.

- [x] **Step 4: 커밋한다**

```bash
git add docs/hyeonmoon/global/README.md
git commit -m "docs: 로깅·Slack 경고 연동 문서 반영"
```

## 완료 조건

- WARN/ERROR 로그가 콘솔·전체 파일·WARN 파일·Slack에 모두 남는다.
- DEBUG/INFO 로그는 전체 파일에만 남고 콘솔·WARN 파일·Slack에는 안 나간다.
- 롤링된 전체 파일과 WARN 파일은 7일이 지나면 자동으로 삭제된다(`maxHistory`).
- Slack 웹훅 HTTP 호출이 실패하거나 느려도 요청 처리 스레드가 블로킹되지 않는다.
- 같은 예외가 짧은 시간에 대량 발생해도 Slack에는 레이트리밋된 건수만 가고 나머지는 요약으로
  처리된다.
- 웹훅 URL 미설정 상태에서도 애플리케이션이 정상 기동하고 다른 로그 출력에는 영향이 없다.
- 비즈니스 코드에 새 로그 호출을 추가하지 않는다(이번 범위 밖).
- 전체 백엔드 테스트가 실패 없이 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
