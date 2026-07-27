# User 개발 계획

`user` 패키지는 User 엔티티와 UserRepository, 배송지 Address를 소유한다. 배송지 API에서는 요청으로 임의의 사용자 ID를 받지 않고, 로그인 사용자 ID인 `Integer userId`를 사용한다.

## 구현 단계

1. [배송지 CRUD](1-address-crud.md)

## API

| Method | Path | 기능 |
|---|---|---|
| GET | `/api/users/me/addresses` | 내 배송지 목록 조회 |
| POST | `/api/users/me/addresses` | 배송지 등록 |
| PUT | `/api/users/me/addresses/{addressId}` | 배송지 수정 |
| DELETE | `/api/users/me/addresses/{addressId}` | 배송지 삭제 |

모든 API는 `@CurrentUser Integer userId`를 사용한다. URL이나 요청 본문에서 임의의 사용자 ID를 받지 않는다.

## 완료 기준

- 다른 사용자의 배송지를 조회·수정·삭제할 수 없다.
- 기본 배송지는 사용자당 최대 하나만 유지된다.
- 기본 배송지 삭제 후 자동 승격은 하지 않는다. 기본 배송지가 없는 상태를 허용한다.
- Address의 배송지 별칭·기본 주소·우편번호는 빈 문자열이 아닌 값으로 검증한다.
- 상세 주소는 없는 경우 null을 허용한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
