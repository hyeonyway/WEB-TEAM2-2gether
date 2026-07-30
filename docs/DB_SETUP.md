# DB 설정 가이드 (H2 → MySQL 전환)

로컬 개발부터 MySQL을 사용하고, 추후 EC2 서버 DB로 연결을 바꿀 때 코드 수정 없이 접속 정보만 바꿀 수 있도록 아래 방식으로 세팅합니다.

## 1. 로컬 MySQL 설치 & DB 생성

```bash
# mac 기준 (brew)
brew install mysql
brew services start mysql

mysql -u root -p
```

```sql
CREATE DATABASE dbidding CHARACTER SET utf8mb4;
```

> **주의**: `schema.sql`에도 `CREATE DATABASE IF NOT EXISTS dbidding ...` 구문이 있지만, 이것만 믿고 이 단계를 건너뛰면 안 됩니다.
> Spring Boot는 앱 시작 시 `spring.datasource.url`(`.../dbidding`)로 **커넥션 풀을 먼저 생성**하는데, 이 시점에 `dbidding` DB가 없으면 `Unknown database 'dbidding'` 에러로 앱이 아예 뜨지 않습니다.
> `schema.sql`은 커넥션이 이미 성공한 뒤에 실행되므로, DB 자체는 반드시 위처럼 **최초 1회 수동으로** 만들어둬야 합니다.

## 2. build.gradle 의존성 추가

H2 대신(또는 함께) MySQL 드라이버 추가:

```gradle
runtimeOnly 'com.mysql:mysql-connector-j'
```

## 3. application.properties → application.yml 변경

`application.properties`는 삭제하고 `application.yml`을 사용합니다.
접속 정보(host, 계정, 비밀번호)는 파일에 직접 쓰지 않고 **환경변수로 분리**합니다.
이렇게 하면 팀원마다 로컬 계정이 달라도 파일은 그대로 공유하고, 나중에 EC2 DB로 옮길 때도 환경변수만 바꾸면 됩니다.

```yaml
spring:
  application:
    name: dbidding

  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:dbidding}?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}

  jpa:
    # 테이블은 schema.sql로 직접 관리하므로 JPA가 생성/변경하지 않게 함
    hibernate:
      ddl-auto: validate
    open-in-view: false

  sql:
    init:
      # schema.sql은 앱이 자동 실행하지 않음 (아래 5번 참고 - 최초 1회 수동 실행 방식)
      mode: never

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` : 개인 환경변수로 설정 (기본값은 로컬 기준: `localhost`, `3306`, `dbidding`, `root`, 빈 비밀번호)
- 비밀번호처럼 커밋하면 안 되는 값은 `.env` 같은 파일에 넣고 `.gitignore` 처리 추천

## 4. 각자 로컬 환경변수 설정 예시

```bash
export DB_USERNAME=root
export DB_PASSWORD=본인비번
```

(IntelliJ에서 실행한다면 Run Configuration > Environment variables에 등록)

## 5. schema.sql 적용

앱이 자동으로 실행해주지 않으므로(`spring.sql.init.mode=never`), DB를 만든 직후 **mysql 클라이언트로 직접 한 번** 실행해서 테이블을 만듭니다.

```bash
mysql -u root -p dbidding < src/main/resources/schema.sql
```

- `CREATE TABLE`에 `IF NOT EXISTS`가 없기 때문에, 이미 테이블이 있는 상태에서 다시 실행하면 `Table ... already exists` 에러가 납니다. 즉 이 명령은 **DB를 새로 만들 때만** 실행하세요 (앱을 켤 때마다 실행하는 게 아님).
- 스키마를 변경해야 하면: 로컬 DB를 `DROP DATABASE dbidding;` 후 다시 만들고 위 명령을 재실행하거나, 바뀐 부분만 `ALTER TABLE`로 직접 반영하세요.
- 운영(EC2) DB도 동일하게 최초 1회만 이 방식으로 스키마를 적용하고, 이후에는 마이그레이션(스키마 변경 SQL)만 별도로 관리하는 걸 권장합니다.

### Docker 서버 시작 시 자동 검증

백엔드 Docker 이미지는 `/app/scripts/start-server.sh`를 entrypoint로 사용한다.

1. 현재 `schema.sql`로 임시 비교 DB를 생성한다.
2. 실제 DB와 임시 DB의 구조 덤프를 비교한다.
3. 구조가 다르면 기존 DB를 SQL gzip 스냅샷으로 저장하고 검증한다.
4. 스냅샷이 정상일 때만 실제 DB를 현재 `schema.sql`로 초기화한다.
5. `required-data/*.sql`을 파일명 오름차순으로 스키마 생성 후 모두 실행한다.
6. 어느 단계든 실패하면 애플리케이션을 시작하지 않는다.

관련 환경변수:

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_SCHEMA_SYNC_MODE` | `reset-on-mismatch` | 불일치 시 초기화. `validate`는 변경 없이 시작을 중단 |
| `DB_SCHEMA_WAIT_SECONDS` | `60` | MySQL 연결 대기 시간 |
| `DB_SNAPSHOT_DIR` | `/app/db-snapshots` | 스냅샷 저장 경로 |
| `SCHEMA_FILE` | `/app/db/resources/schema.sql` | 기준 스키마 파일 |
| `INITIAL_DATA_DIR` | `/app/db/resources/required-data` | 필수 초기 데이터 SQL 디렉터리 |

초기 데이터는 실행 순서를 파일명 접두사로 관리한다.

```text
src/main/resources/required-data/
├── 001-pokemon-card.sql
├── 002-user.sql
├── 003-auction-bid-item-statistics.sql
├── 004-dashboard-current-auctions.sql
└── 005-notification-seed.sql
```

DB 초기화 시 비어 있지 않은 `.sql` 파일만 정렬된 순서대로 실행한다. 하나라도
실패하면 이후 파일과 애플리케이션 실행을 즉시 중단한다.

`004-dashboard-current-auctions.sql`은 `DEBUG_USER_ID=1` 대시보드 확인을 위한
진행 경매 50개를 예약 ID `3000001`~`3000050`에 생성한다. 실행 시각마다 오늘
00시에 시작하고 미래에 종료되는 `OPEN`/`ENDING` 데이터로 다시 만들어진다.
각 경매에는 다른 시드 사용자의 입찰 2~5건이 포함되며, 사용자 `1`은 일부 경매에서
최고 입찰자이고 일부 경매에서는 상회 입찰된 참여자다.

같은 파일에서 최근 낙찰 탭 확인용 `ENDED` 경매 12개도 예약 ID
`3000101`~`3000112`에 생성한다. 사용자 `1`의 최종 입찰은 `WON`, 이전 참여자들의
입찰은 `LOST`로 기록되며, 종료 시각은 최근 1일부터 12일까지 역순으로 구성된다.
모든 경매에는 카드 메타데이터의 이미지 경로가 함께 등록된다.

`005-notification-seed.sql`은 `004`가 만든 경매를 재사용해 `DEBUG_USER_ID=1`의
알림 목록에 클릭해볼 데이터를 채운다. 경매 등록 알림(`3000001`~`3000005`),
상회 입찰 알림(`3000004`, `3000007`, `3000010` — `004`의 상회 입찰 그룹과 동일),
낙찰 알림(`3000101`~`3000106`)을 만들며, 일부는 `is_read = true`로 시드해
읽음/안읽음 필터를 바로 확인할 수 있게 한다.

DB 계정은 실제 DB와 `dbidding_schema_check_%` 비교용 DB를 생성·삭제할 수
있어야 한다.

스냅샷을 컨테이너 재생성 후에도 보존하려면 Docker Compose에서 영속 볼륨을
`/app/db-snapshots`에 마운트해야 한다.

```yaml
services:
  backend:
    volumes:
      - db_snapshots:/app/db-snapshots

volumes:
  db_snapshots:
```

운영 환경에서 자동 초기화를 허용하지 않으려면 다음 값을 사용한다.

```bash
DB_SCHEMA_SYNC_MODE=validate
```

## 6. Entity 작성 시 주의

- `ddl-auto=validate`이므로 JPA가 테이블을 만들어주지 않습니다. **테이블 구조는 schema.sql이 원본(source of truth)**이고, Entity(`@Entity`, `@Column` 등)는 그 구조에 정확히 맞춰서 직접 작성해야 합니다.
- 컬럼명/타입/제약조건이 schema.sql과 Entity가 어긋나면 앱 실행 시 `validate` 단계에서 에러가 발생합니다 (의도된 안전장치이니 무시하지 말고 schema.sql 또는 Entity를 맞춰주세요).

## 7. 나중에 EC2 서버 DB로 연결할 때

코드/파일 수정 없이 배포 환경의 환경변수만 설정하면 됩니다:

```bash
DB_HOST=<EC2 주소>
DB_PORT=3306
DB_NAME=dbidding
DB_USERNAME=<서버 계정>
DB_PASSWORD=<서버 비밀번호>
```
