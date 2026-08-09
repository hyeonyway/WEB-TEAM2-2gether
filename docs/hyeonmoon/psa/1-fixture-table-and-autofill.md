# PSA 인증 임시 테이블과 자동 채움 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실제 PSA Open API는 구독이 필요해 이번 범위에서 쓰지 않는다. 대신 팀이 직접 등록한
소수의 인증번호로 "번호만 입력하면 카드까지 자동 선택되고 폼 전체가 채워지는" 경험을 제공한다.
등록 안 된 번호는 실패로만 처리하고(공식 기반 fallback 없음) 아무 것도 채우지 않는다 — 실제
PSA API도 없는 번호는 조회 실패를 반환하는 게 정상이므로, 지금의 "마지막 자리 숫자로 등급 계산"
공식보다 이쪽이 더 사실적이다.

**Architecture:**

```text
GET /api/psa-certifications/{number}
  등록된 번호 → 200 { itemId, gradeType: "psa", psaGrade, population, issuedYear, cardNumber }
  미등록 번호 → 404

GET /api/psa-certifications/sample
  → 200 { certificationNumber }   (등록된 번호 중 하나 — "예시 인증번호 채우기" 버튼용)
```

핵심 설계 결정: fixture 테이블은 `psa_grade`를 직접 저장하지 않고 `item_id`로
`card_metadata`를 참조한다. 등급뿐 아니라 카드 번호와 발행 연도도 카드 자체의 속성이므로
`card_metadata`가 소유하고 fixture에는 중복하지 않는다. 그래서 응답 등급은 항상 그 카드의 실제
`card_metadata.psa_grade`와 같다는 게 스키마 레벨로 보장된다 — `AuctionCommandService.validatePsaCertification()`
([AuctionCommandService.java:335](../../../backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java:335))의
"인증 등급과 선택한 카드 등급이 일치해야 한다" 검증과 fixture 데이터가 절대 어긋날 수 없다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, React/TypeScript(프론트)

## 조사 근거 — 현재 상태

- 지금 mock(`PsaCertificationMockService.java:8-15`)은 인증번호 마지막 자리로 등급을 계산하는
  순수 공식이고, 카드 신원(itemId/cardName 등)은 전혀 반환하지 않는다.
- 실제 카드 선택은 이름 검색(`frontend/src/pages/sell/index.tsx`의 `CardSearchResults` →
  `selectCard`/`applyCardSelection`)으로만 이뤄지고, PSA 조회 결과는 **이미 선택된 카드의
  variant 중에서만** 등급이 일치하는 걸 찾아 재선택한다(`lookupPsa`, `index.tsx:103-122`,
  `cardVariants.length>0` 가드). 카드를 아직 안 골랐으면 PSA 조회는 등급/population만 폼에
  써넣고 카드 선택에는 아무 영향이 없다 — 이번 작업이 없애려는 지점이다.
- `AuctionCreateRequest.itemId`는 `@NotNull`이라 결국 사용자가 카드를 하나 골라야 등록이
  된다 — fixture가 `itemId`를 직접 알려주면 이 선택 단계 자체를 건너뛸 수 있다.
- `card_metadata`(schema.sql:76-94)가 이미 "카드+등급"을 한 행으로 갖고 있다(같은 카드도
  등급별로 별도 행). 그래서 fixture는 `item_id` FK 하나만 있으면 그 카드의 이름·세트·이미지·
  등급까지 전부 따라온다 — 프론트가 이미 갖고 있는 `fetchCardDetail(id)`(카드 검색 결과 클릭 시
  쓰는 것과 동일 API)로 나머지를 채우면 되므로 응답 페이로드를 크게 늘릴 필요가 없다.

## Global Constraints

- fixture 테이블에 `psa_grade`/`gradeType` 컬럼을 별도로 두지 않는다 — 항상 `item_id` FK를 통해
  `card_metadata.psa_grade`에서 유도한다(등급 불일치 원천 차단).
- 미등록 인증번호는 404만 반환한다. 마지막 자리 공식 같은 fallback을 추가하지 않는다.
- 프론트에서 조회 실패는 입력창 아래 빨간 인라인 텍스트("등록된 PSA 번호가 아닙니다.")만
  보여준다 — "예시 번호를 써보세요" 같은 유도 문구는 넣지 않는다(이미 버튼이 위에 있음).
- "예시 인증번호 채우기" 버튼은 PSA 인증번호 입력창 **위**에 둔다.
- OCR(카드/라벨 사진 인식) 관련 버튼이나 자리는 추가하지 않는다 — 이번 범위 밖.
- `AuctionCreateRequest`, `/api/auctions` 계약, `AuctionCommandService.validatePsaCertification()`의
  검증 로직 자체는 변경하지 않는다 — 이번 작업은 "폼을 얼마나 자동으로 채워주는가"만 바꾼다.
- `card_sets`는 카드 컬렉션 기준 데이터로 유지한다. PSA fixture에 세트 정보를 중복하거나
  `card_sets`를 제거하지 않는다.

---

### Task 1: PSA 인증 fixture 테이블을 추가한다

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/resources/required-data/008-psa-certification-fixture-seed.sql`

**Interfaces:**
- Produces: `psa_certification_fixtures(id, certification_number UNIQUE, item_id FK → card_metadata.id)`

- [x] **Step 1: 스키마에 테이블을 추가한다**

```sql
CREATE TABLE psa_certification_fixtures
(
    id                    INT         NOT NULL AUTO_INCREMENT,
    certification_number  VARCHAR(10) NOT NULL,
    item_id               INT         NOT NULL,

    CONSTRAINT pk_psa_certification_fixtures PRIMARY KEY (id),
    CONSTRAINT uk_psa_certification_fixtures_number UNIQUE (certification_number),
    CONSTRAINT fk_psa_certification_fixtures_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id)
);
```

`population`은 저장하지 않는다 — 실제 PSA population처럼 보이는 임의 4자리 숫자면 충분하고
카드마다 고정될 필요가 없으므로, 응답 생성 시점에 인증번호를 시드로 계산한다(기존
`PsaCertificationMockService`의 population 계산 방식 재사용).

- [x] **Step 2: 시드 데이터를 추가한다**

`001-pokemon-card.sql`에 이미 있는 PSA 등급 카드(예: `item_id=1`, `피카츄 P 메가 에볼루션
프로모카드`, `PSA 10`) 중 2~3개를 골라 실제처럼 보이는 7~10자리 인증번호와 매핑한다.

```sql
INSERT INTO `psa_certification_fixtures` (`certification_number`, `item_id`)
VALUES
  ('12345678', 1),
  ('87654321', 2)
ON DUPLICATE KEY UPDATE `item_id` = VALUES(`item_id`);
```

- [x] **Step 3: 로컬 DB에 적용하고 확인한다**

`DB_SETUP.md` 절차대로 `schema.sql` → `required-data/*.sql` 순서로 적용한 뒤,
`SELECT * FROM psa_certification_fixtures;`로 확인한다.

- [x] **Step 4: 커밋한다** (`cf3e99b`)

```bash
git add backend/src/main/resources/schema.sql \
  backend/src/main/resources/required-data/008-psa-certification-fixture-seed.sql
git commit -m "feat: PSA 인증 fixture 테이블과 시드 데이터 추가"
```

### Task 2: PSA 조회 서비스를 fixture 기반으로 교체한다

**Files:**
- Create: `backend/src/main/java/com/dbidding/psa/PsaCertificationFixtureRepository.java`
- Create: `backend/src/main/java/com/dbidding/card/service/CardPsaGradeQueryService.java`
- Modify: `backend/src/main/java/com/dbidding/psa/PsaCertificationMockService.java`
  (→ `PsaCertificationService`로 이름 변경)
- Modify: `backend/src/main/java/com/dbidding/psa/PsaCertificationMockController.java`
  (→ `PsaCertificationController`로 이름 변경)
- Create: `backend/src/main/java/com/dbidding/psa/exception/PsaCertificationNotFoundException.java`
- Modify: `backend/src/test/java/com/dbidding/psa/PsaCertificationMockServiceTest.java`
  (→ `PsaCertificationServiceTest`)

**Interfaces:**
- Removes: 마지막 자리 숫자 기반 등급 계산 로직
- Produces:
  - `GET /api/psa-certifications/{certificationNumber}` → `PsaCertificationResponse(itemId, gradeType, psaGrade, population)` 또는 404
  - `GET /api/psa-certifications/sample` → `{ certificationNumber }` (fixture 중 하나, 없으면 404)
- Consumes: `CardPsaGradeQueryService` — `item_id`로 `psa_grade`만 조회한다. PSA는 card의
  Repository나 Entity를 직접 참조하지 않는다.

- [x] **Step 1: 실패하는 테스트를 먼저 작성한다**

등록된 인증번호 → `itemId`/`psaGrade`(= 해당 카드의 `card_metadata.psa_grade`)/양수
`population` 반환. 미등록 번호 → `PsaCertificationNotFoundException`(404). `/sample`은
fixture 중 하나의 `certification_number`를 반환.

- [x] **Step 2: 테스트가 실패하는지 확인한다**

```bash
cd backend
./gradlew test --tests 'com.dbidding.psa.*'
```

- [x] **Step 3: 서비스·컨트롤러를 구현한다**

```java
public PsaCertificationResponse lookup(String certificationNumber) {
    PsaCertificationFixture fixture = fixtureRepository.findByCertificationNumber(certificationNumber)
        .orElseThrow(PsaCertificationNotFoundException::new);
    String psaGrade = cardPsaGradeQueryService.findPsaGrade(fixture.getItemId())
        .orElseThrow(PsaCertificationNotFoundException::new);
    return new PsaCertificationResponse(
        fixture.getItemId(),
        "psa",
        normalizeGrade(psaGrade),
        population(certificationNumber)
    );
}
```

`population(certificationNumber)`는 기존 mock의 해시 기반 계산을 그대로 재사용한다.
`PsaCertificationNotFoundException`은 `@ResponseStatus(HttpStatus.NOT_FOUND)`.

- [x] **Step 4: 테스트를 통과시키고 커밋한다** (`cf3e99b`, `daa3ec6`)

```bash
./gradlew test --tests 'com.dbidding.psa.*'
git add backend/src/main/java/com/dbidding/psa backend/src/test/java/com/dbidding/psa
git commit -m "feat: PSA 조회를 fixture 테이블 기반으로 교체하고 미등록 번호는 404 반환"
```

### Task 3: 프론트 — 자동 채움과 실패 표시, 예시 버튼

**Files:**
- Modify: `frontend/src/api/sellApi.ts`
- Modify: `frontend/src/pages/sell/index.tsx`
- Modify: `frontend/src/dto/sellDto.ts` (`PsaCertification`에 `itemId` 추가)

**Interfaces:**
- Modifies: `lookupPsa()`가 성공 시 `fetchCardDetail(certification.itemId)`로 카드를 완전히
  선택하고, 실패(404) 시 폼은 그대로 두고 에러 상태만 세팅
- Produces: "예시 인증번호 채우기" 버튼 — `/api/psa-certifications/sample` 호출 후 입력값 채움

- [x] **Step 1: 실패 응답 처리를 추가한다**

`lookupPsa()`에서 404를 받으면 `setPsaStatus('error')`(또는 동등한 상태) + 에러 메시지
상태만 세팅하고 `setForm(...)`을 호출하지 않는다. 인증번호 입력창 아래에 실패 시에만
"등록된 PSA 번호가 아닙니다." 빨간 텍스트를 렌더링한다. 안내 문구나 버튼 유도 텍스트는
추가하지 않는다.

- [x] **Step 2: 성공 시 카드 자동 선택으로 바꾼다**

`certification.itemId`가 있으면 `fetchCardDetail(itemId)` 결과로 PSA 전용 카드 선택 상태를
적용해 카드 검색 없이 카드명·세트·언어·등급·population을 채운다. 기존의 "이미 선택된 variant
중에서 등급을 찾기" 흐름은 제거한다.

- [x] **Step 3: "예시 인증번호 채우기" 버튼을 추가한다**

PSA 인증번호 입력창 위에 버튼을 두고, 클릭 시 `GET /api/psa-certifications/sample`로 받은
번호를 입력 상태에 채운다(자동 조회까지 트리거할지는 UX 취향 — 채우기만 하고 조회는 사용자가
누르게 해도 된다).

- [x] **Step 4: 테스트로 확인하고 커밋한다**

판매 등록 화면 테스트에서 등록된 인증번호의 카드 자동 선택, 미등록 번호의 기존 폼 유지와 빨간
텍스트, 예시 버튼의 입력값 채움만 수행(자동 조회 없음)을 확인한다.

```bash
git add frontend/src/api/sellApi.ts frontend/src/pages/sell/index.tsx frontend/src/dto/sellDto.ts
git commit -m "feat: PSA 조회 성공 시 카드 자동 선택, 실패 시 인라인 오류, 예시 번호 버튼 추가"
```

### Task 4: 문서 정리

**Files:**
- Modify: `docs/hyeonmoon/psa/README.md`

- [x] **Step 1: 전체 백엔드 테스트를 실행한다**

```bash
cd backend
./gradlew clean test
```

결과: 로컬 DB에 `001-pokemon-card.sql`과 `008-psa-certification-fixture-seed.sql`을 순서대로
적용해 fixture 3건을 확인했고, `./gradlew clean test`가 성공했다.

- [x] **Step 2: README 구현 단계를 갱신하고 커밋한다**

```bash
git add docs/hyeonmoon/psa/README.md
git commit -m "docs: PSA fixture 자동 채움 문서 반영"
```

### 후속 변경: 카드 메타데이터 자동 채움과 등급 UI

발행 연도와 카드 번호는 PSA 인증번호마다 달라지는 값이 아니라 카드 카탈로그의 속성이다.
따라서 `psa_certification_fixtures`에는 추가하지 않고, 연결된 `card_metadata`에서 조회한다.
`card_sets`는 카드 묶음의 기준 데이터이므로 그대로 둔다.

- [x] `card_metadata`에 nullable `issued_year CHAR(4)`, `card_number VARCHAR(50)`를 추가했다.
- [x] PSA fixture 시드의 연결 카드 3건에 발행 연도·카드 번호를 입력했다.
- [x] PSA 응답에 두 값을 포함하고, 판매 등록 폼의 발행 연도·카드 번호를 자동 채운다.
- [x] OCR 보조 기능은 판매 등록 화면에서 제거했다. API 자체의 제거는 이번 범위에 포함하지 않는다.
- [x] PSA 인증 전에는 자체 평가만, 인증 완료 후에는 PSA 등급만 표시한다. 인증번호를 수정하면
  자체 평가 상태로 되돌린다.
- [x] PSA 서비스·카드 조회 서비스·판매 등록 화면 회귀 테스트를 실행했다.

## 완료 조건

- 등록된 PSA 인증번호를 입력하면 카드 선택부터 등급·population까지 전부 자동으로 채워진다.
- 등록 안 된 번호는 입력창 아래 빨간 텍스트만 뜨고 폼은 바뀌지 않는다 — fallback 계산 없음.
- fixture가 반환하는 등급은 항상 연결된 `card_metadata.psa_grade`와 같다(스키마 FK로 보장).
- "예시 인증번호 채우기" 버튼이 입력창 위에 있고, 실제 등록된 번호를 채워준다.
- OCR 관련 UI가 추가되지 않는다.
- 발행 연도와 카드 번호는 fixture가 아닌 연결된 `card_metadata`가 소유한다.
- PSA 인증 전에는 자체 평가만 보이고, 인증 완료 후에는 PSA 등급만 보인다.
- `AuctionCreateRequest`/`/api/auctions` 계약과 기존 등급 일치 검증 로직은 변경되지 않는다.
- 전체 백엔드 테스트가 실패 없이 통과한다.

> 이 문서는 AI의 도움을 받아 작성하였습니다
