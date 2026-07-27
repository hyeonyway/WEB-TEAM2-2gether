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

## 5. schema.sql 적용 (최초 1회, 수동 실행)

앱이 자동으로 실행해주지 않으므로(`spring.sql.init.mode=never`), DB를 만든 직후 **mysql 클라이언트로 직접 한 번** 실행해서 테이블을 만듭니다.

```bash
mysql -u root -p dbidding < src/main/resources/schema.sql
```

- `CREATE TABLE`에 `IF NOT EXISTS`가 없기 때문에, 이미 테이블이 있는 상태에서 다시 실행하면 `Table ... already exists` 에러가 납니다. 즉 이 명령은 **DB를 새로 만들 때만** 실행하세요 (앱을 켤 때마다 실행하는 게 아님).
- 스키마를 변경해야 하면: 로컬 DB를 `DROP DATABASE dbidding;` 후 다시 만들고 위 명령을 재실행하거나, 바뀐 부분만 `ALTER TABLE`로 직접 반영하세요.
- 운영(EC2) DB도 동일하게 최초 1회만 이 방식으로 스키마를 적용하고, 이후에는 마이그레이션(스키마 변경 SQL)만 별도로 관리하는 걸 권장합니다.

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
