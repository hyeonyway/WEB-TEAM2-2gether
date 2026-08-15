# CI 파이프라인 속도 개선·PR 품질 검증 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드·프론트엔드 변경을 PR 단계에서 검증하고, 반복 실행을 취소·Gradle 빌드 캐시로 단축하며, JaCoCo 결과를 PR에서 확인할 수 있게 한다. 커버리지 기준 미달은 이번 이슈에서 빌드를 차단하지 않는다.

**Architecture:** 각 워크플로우는 `push`와 `pull_request`에서 같은 테스트를 수행하되, 이미지 발행·배포·Slack 배포 알림은 `push` 이벤트에만 남긴다. 백엔드는 `gradle/actions/setup-gradle@v4`의 task-output 캐시를 사용하고 JaCoCo XML/HTML을 만들며, 같은 저장소에서 열린 PR에만 커버리지 요약 코멘트를 남긴다. 검증 기준선이 없는 상태에서는 `jacocoTestCoverageVerification`을 CI에 연결하거나 `violationRules`를 추가하지 않는다.

**Tech Stack:** GitHub Actions, Gradle 8/JaCoCo, Java 21, Node.js 22, Vitest, TypeScript

---

## 확정 범위와 실행 규칙

| 구분 | PR | `dev` push | `main` push |
|---|---|---|---|
| 백엔드 테스트·JaCoCo 리포트 | 실행 | 실행 | 실행 |
| 프론트 Vitest·타입 검사 | 실행 | 실행 | 실행 |
| 개발 이미지 빌드 | 스킵 | 실행 | 스킵 |
| 운영 이미지 발행·배포 | 스킵 | 스킵 | 실행 |
| S3·CloudFront 프론트 배포 | 스킵 | 스킵 | 실행 |
| Slack 배포 알림 | 스킵 | 실행 | 실행 |

- 두 워크플로우 모두 PR 대상 브랜치를 `dev`, `main`으로 한정하고 현재의 `paths` 필터를 그대로 둔다.
- `concurrency.group`은 워크플로우와 PR 번호(또는 ref)를 조합한다. 같은 PR/브랜치의 새 실행이 시작되면 이전 실행은 `cancel-in-progress: true`로 취소한다.
- `pull_request`가 fork에서 온 경우 토큰에 PR 쓰기 권한이 없으므로, JaCoCo 코멘트 단계는 `github.event.pull_request.head.repo.full_name == github.repository`일 때만 실행한다. 테스트와 리포트 생성 자체는 계속 실행한다.
- `notify`는 `push`에만 한정한다. PR 이벤트에 `github.event.before`가 없어서 Slack 변경 파일 계산이 실패하는 문제를 막고, PR 검증을 배포 알림으로 오인하지 않게 한다.

## 커버리지 정책

이번 이슈는 **관측 단계**다. `jacocoTestReport`로 XML·HTML을 생성하고 XML을 아티팩트 및 PR 요약의 입력으로 사용한다. `jacocoTestCoverageVerification` task는 이후 품질 게이트 도입 지점으로만 정의하며, 다음 둘은 이번에 하지 않는다.

- 최소 line/branch coverage `violationRules`를 정하지 않는다.
- CI 명령에 `jacocoTestCoverageVerification`을 넣지 않는다.

따라서 현재 테스트의 실제 커버리지 기준선을 팀이 확인한 뒤, 별도 이슈에서 범위(전체/변경분), 제외 패키지, 임계값, 점진적 상향 정책을 합의해 차단 게이트를 추가한다.

### Task 1: 백엔드 Gradle에 JaCoCo 리포트 기반을 추가한다

**Files:**
- Modify: `backend/build.gradle`

**Interfaces:**
- Produces: `build/reports/jacoco/test/jacocoTestReport.xml`, HTML 리포트 디렉터리
- Preserves: `./gradlew test`의 JUnit Platform·UTC 테스트 JVM 설정

- [ ] **Step 1: JaCoCo 플러그인을 추가한다**

  기존 `plugins` 블록에 `id 'jacoco'`를 추가한다. 새 런타임 의존성이나 테스트 프레임워크는 추가하지 않는다.

- [ ] **Step 2: 리포트 task를 구성한다**

  `jacocoTestReport`가 `test` 후 실행되게 하고 XML과 HTML을 모두 `required = true`로 설정한다. GitHub Actions의 PR 액션은 XML을, 개발자는 HTML을 사용한다.

- [ ] **Step 3: 비차단 verification task를 명시한다**

  `jacocoTestCoverageVerification`이 리포트 생성 뒤 실행 가능한 task가 되도록 의존성만 연결한다. `violationRules`와 최소 커버리지 수치는 추가하지 않는다. 이 task를 CI 명령에서 호출하지 않는다.

- [ ] **Step 4: 로컬 리포트를 검증한다**

  Run: `cd backend && ./gradlew clean test jacocoTestReport --no-daemon`

  Expected: 테스트가 완료되고 `build/reports/jacoco/test/jacocoTestReport.xml` 및 HTML 리포트가 생성된다. 이 단계는 커버리지 부족으로 실패하지 않는다.

- [ ] **Step 5: 커밋한다**

  ```bash
  git add backend/build.gradle
  git commit -m "chore: JaCoCo 테스트 리포트 구성 추가"
  ```

### Task 2: 백엔드 워크플로우에 PR 검증·캐시·리포트 공개를 추가한다

**Files:**
- Modify: `.github/workflows/backend-deploy.yml`

**Interfaces:**
- Consumes: Task 1의 JaCoCo XML 경로
- Produces: PR 테스트 체크, JaCoCo 아티팩트, 내부 PR의 커버리지 코멘트
- Preserves: `dev` 이미지 빌드, `main` 이미지 발행·EC2 배포, 기존 MySQL/Redis 서비스

- [ ] **Step 1: 트리거와 동시 실행 제어를 추가한다**

  `on.pull_request.branches`에 `[dev, main]`을, `paths`에는 기존 backend workflow 범위를 그대로 적용한다. workflow 최상위에 다음 의미의 concurrency를 추가한다.

  ```yaml
  concurrency:
    group: backend-ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
    cancel-in-progress: true
  ```

- [ ] **Step 2: Gradle task-output 캐시로 교체한다**

  JDK 21 설정은 유지하되 `actions/setup-java@v4`의 `cache: gradle` 입력을 제거한다. 그 다음 단계에서 `gradle/actions/setup-gradle@v4`를 사용한다.

- [ ] **Step 3: 테스트와 JaCoCo 리포트를 한 번에 실행한다**

  `Run Gradle tests` 명령을 `./gradlew test jacocoTestReport --no-daemon`으로 바꾼다. 이후 `actions/upload-artifact`로 `backend/build/reports/jacoco/test/`를 업로드한다. 테스트 실패에도 리포트가 남도록 아티팩트 단계는 `if: always()`로 설정하고, 파일이 없을 때는 workflow를 추가로 실패시키지 않도록 `if-no-files-found: warn`을 사용한다.

- [ ] **Step 4: 내부 PR에만 커버리지 코멘트를 추가한다**

  `madrapps/jacoco-report` 액션을 XML 리포트 경로와 함께 사용하고, `pull_request` 및 동일 저장소 head 조건을 모두 둔다. 필요한 최소 권한(`contents: read`, `pull-requests: write`)은 해당 job 또는 workflow에 명시한다. fork PR은 테스트·아티팩트만 남기고 코멘트는 스킵한다.

- [ ] **Step 5: 배포 job과 Slack 알림의 이벤트 조건을 명시한다**

  `build-dev`는 `github.event_name == 'push' && github.ref_name == 'dev'`로 제한한다. `publish-prod-image`와 `deploy-prod`는 현재처럼 `push/main`으로 제한한다. `notify`는 `always()`와 test 결과 조건에 더해 `github.event_name == 'push'`를 요구하고 checkout의 `fetch-depth`를 `50`으로 낮춘다.

- [ ] **Step 6: 로컬 정적 검증을 실행하고 원격 검증 항목을 분리한다**

  Run: `ruby -e "require 'yaml'; YAML.load_file('.github/workflows/backend-deploy.yml')" && git diff --check`

  Expected: 로컬 YAML 문법과 변경의 공백 오류가 없고, diff에서 `pull_request`, concurrency, setup-gradle, JaCoCo 실행·아티팩트·PR 조건, 명시적인 push-only 배포 조건을 확인한다.

  원격 Actions 검증은 커밋을 사용자가 push하고 PR을 연 뒤 수행한다. 해당 PR 실행에서 `test`는 실행되고 `build-dev`, `publish-prod-image`, `deploy-prod`, `notify`는 스킵되는지 확인한다. `gh workflow view`는 원격의 기존 workflow만 읽으므로 로컬 수정본 검증 명령으로 사용하지 않는다.

- [ ] **Step 7: 커밋한다**

  ```bash
  git add .github/workflows/backend-deploy.yml
  git commit -m "chore: 백엔드 PR 검증과 Gradle 캐시 추가"
  ```

### Task 3: 프론트엔드 검증과 배포를 분리한다

**Files:**
- Modify: `.github/workflows/frontend-deploy.yml`
- Reference: `frontend/package.json`

**Interfaces:**
- Consumes: `npm ci`, `npm run test`, `npm run typecheck`
- Preserves: main push의 Vite build, AWS OIDC 인증, S3 sync, CloudFront invalidation

- [ ] **Step 1: push·PR 트리거와 concurrency를 추가한다**

  현재 main-only인 `push.branches`를 `[dev, main]`으로 넓혀 dev push에서도 프론트 검증 job이 실행되게 한다. backend와 같은 원칙으로 `pull_request.branches: [dev, main]`과 `frontend/**`, workflow 파일 경로 필터를 추가한다. 별도의 `frontend-ci-...` concurrency group을 사용해 backend 실행과 충돌하지 않게 한다.

- [ ] **Step 2: `test` job을 분리한다**

  checkout, Node 22/npm 캐시, `npm ci` 뒤에 `npm run test`, `npm run typecheck`를 순서대로 실행하는 `test` job을 만든다. `package.json`의 기존 script를 사용하고 명령을 중복 정의하지 않는다.

- [ ] **Step 3: 배포 job을 push/main 전용으로 만든다**

  기존 `deploy` job에 `needs: test`와 다음 가드를 둔다.

  ```yaml
  if: github.event_name == 'push' && github.ref_name == 'main'
  ```

  deploy job 안에서도 독립 실행을 위해 `npm ci`와 Vite build를 유지한다. `npm run typecheck`는 test job에서 이미 수행했으므로 build는 `npx vite build`만 실행한다.

- [ ] **Step 4: 로컬 정적 검증을 실행하고 원격 PR 동작을 확인한다**

  Run: `ruby -e "require 'yaml'; YAML.load_file('.github/workflows/frontend-deploy.yml')" && git diff --check`

  Expected: 로컬 YAML 문법과 변경의 공백 오류가 없고, diff에서 `push.branches: [dev, main]`, PR 트리거와 `test` job, push/main 조건 및 `needs: test`를 가진 deploy job을 확인한다.

  사용자가 커밋을 push하고 PR을 연 뒤 Actions 실행에서 테스트·타입 검사는 실행되고 AWS/S3/CloudFront 단계는 실행되지 않는지 확인한다. `gh workflow view`는 원격의 기존 workflow만 읽으므로 로컬 수정본 검증 명령으로 사용하지 않는다.

- [ ] **Step 5: 커밋한다**

  ```bash
  git add .github/workflows/frontend-deploy.yml
  git commit -m "chore: 프론트 PR 테스트와 배포 분리"
  ```

### Task 4: 변경 결과를 검증하고 이슈와 함께 마무리한다

**Files:**
- Modify: `docs/hyeonmoon/global/2-ci-pipeline-quality-gates.md`

- [ ] **Step 1: 로컬 전체 검증을 실행한다**

  Run: `cd backend && ./gradlew clean test jacocoTestReport --no-daemon`

  Run: `cd frontend && npm run test && npm run typecheck && npm run build`

  Expected: 각 명령이 성공하고 JaCoCo XML/HTML 및 프론트 build 산출물이 생성된다. 테스트 소스가 없는 모듈은 `NO-SOURCE`를 성공한 테스트로 기록하지 않는다.

- [ ] **Step 2: 변경 파일과 보안 조건을 검토한다**

  Run: `git diff --check && git diff -- .github/workflows/backend-deploy.yml .github/workflows/frontend-deploy.yml backend/build.gradle`

  Expected: 공백 오류가 없고, PR에서 AWS·GHCR·SSH·Slack 배포 경로가 실행되지 않으며, fork PR에 쓰기 권한을 요구하는 JaCoCo 코멘트 단계가 없다.

- [ ] **Step 3: 문서 상태를 갱신하고 커밋한다**

  구현 결과와 실제 검증 명령의 결과를 문서 상단 또는 작업 항목에 반영하고, 이 문서만 별도 커밋한다.

  ```bash
  git add docs/hyeonmoon/global/2-ci-pipeline-quality-gates.md
  git commit -m "docs: CI 품질 검증 도입 계획 추가"
  ```

## 제외 범위

- JaCoCo 최소 커버리지 수치와 차단 gate
- 코드 커버리지 감소를 막는 diff coverage 정책
- 외부 fork PR에 대한 쓰기 권한 부여 또는 `pull_request_target` 사용
- 실제 배포 환경·AWS 권한·Slack 웹훅의 재설계

> 이 문서는 codex의 도움을 받아 작성하였습니다
