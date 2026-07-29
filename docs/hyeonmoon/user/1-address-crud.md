# Address CRUD Implementation Plan

**Goal:** 로그인 사용자가 자신의 배송지를 등록·조회·수정·삭제할 수 있는 CRUD를 구현한다.

**Architecture:** Address는 `Integer userId` scalar FK만 보유한다. 모든 조회와 변경 쿼리는 `addressId`와 현재 로그인 `userId`를 함께 조건으로 사용해 다른 사용자의 데이터 접근을 차단한다.

**Tech Stack:** Java 21, Spring Data JPA, Spring MVC, Bean Validation, JUnit 5, Mockito, MockMvc

## Global Constraints

- `users` Entity와 UserRepository를 `user` 패키지에서 import하지 않는다.
- Controller는 URL이나 본문에서 userId를 받지 않고 `@CurrentUser Integer userId`를 사용한다.
- 배송지 별칭은 `VARCHAR(50)`, 기본 주소는 `VARCHAR(255)`다.
- 상세 주소는 입력하지 않을 수 있으므로 nullable이다.
- 국내 우편번호는 숫자 5자리 `CHAR(5)`다.
- 사용자당 기본 배송지는 최대 하나만 유지한다.
- 엔티티는 `@Getter`와
  `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용하며 setter를 만들지 않는다.

---

### Task 1: Address 엔티티와 Repository

**Files:**
- Create: `backend/src/main/java/com/dbidding/user/domain/Address.java`
- Create: `backend/src/main/java/com/dbidding/user/repository/AddressRepository.java`
- Test: `backend/src/test/java/com/dbidding/user/domain/AddressTest.java`

**Interfaces:**
- Produces: `Address.create(Integer userId, String addressName, String address, String detailedAddress, String postalCode, boolean defaultAddress)`
- Produces: `List<Address> AddressRepository.findAllByUserIdOrderByIdAsc(Integer userId)`
- Produces: `Optional<Address> AddressRepository.findByIdAndUserId(Integer id, Integer userId)`

- [ ] **Step 1: 생성과 수정 실패 테스트 작성**

```java
@Test
void 배송지의_표시명과_주소를_수정한다() {
    Address address = Address.create(1, "집", "서울시", "101호", "01234", false);

    address.update("회사", "서울시 강남구", "5층", "06236", true);

    assertThat(address.getAddressName()).isEqualTo("회사");
    assertThat(address.isDefault()).isTrue();
}
```

- [ ] **Step 2: 스키마와 동일하게 엔티티 구현**

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "addresses")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "address_name", nullable = false, length = 50)
    private String addressName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "detailed_address", length = 255)
    private String detailedAddress;

    @Column(name = "postal_code", nullable = false, length = 5)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

}
```

- [ ] **Step 3: 소유권 포함 Repository 작성**

```java
public interface AddressRepository extends JpaRepository<Address, Integer> {
    List<Address> findAllByUserIdOrderByIdAsc(Integer userId);
    Optional<Address> findByIdAndUserId(Integer id, Integer userId);

    @Modifying
    @Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
    void clearDefaultByUserId(Integer userId);
}
```

- [ ] **Step 4: 단위 테스트 통과**

```bash
./gradlew test --tests com.dbidding.user.domain.AddressTest
```

Expected: PASS.

### Task 2: 요청·응답 DTO

**Files:**
- Create: `backend/src/main/java/com/dbidding/user/dto/AddressRequest.java`
- Create: `backend/src/main/java/com/dbidding/user/dto/AddressResponse.java`

- [ ] **Step 1: 검증 규칙 작성**

```java
public record AddressRequest(
    @NotBlank @Size(max = 50) String addressName,
    @NotBlank @Size(max = 255) String address,
    @Size(max = 255) String detailedAddress,
    @NotBlank @Pattern(regexp = "\\d{5}") String postalCode,
    boolean defaultAddress
) {}
```

- [ ] **Step 2: 응답 변환 작성**

응답은 `id`, `addressName`, `address`, `detailedAddress`, `postalCode`, `defaultAddress`만 포함하고 userId는 노출하지 않는다.

### Task 3: AddressService

**Files:**
- Create: `backend/src/main/java/com/dbidding/user/service/AddressService.java`
- Create: `backend/src/main/java/com/dbidding/user/exception/AddressNotFoundException.java`
- Test: `backend/src/test/java/com/dbidding/user/service/AddressServiceTest.java`

**Interfaces:**
- Produces: `List<AddressResponse> getAll(Integer userId)`
- Produces: `AddressResponse create(Integer userId, AddressRequest request)`
- Produces: `AddressResponse update(Integer userId, Integer addressId, AddressRequest request)`
- Produces: `void delete(Integer userId, Integer addressId)`

- [ ] **Step 1: 다른 사용자 주소 수정 차단 테스트**

```java
given(addressRepository.findByIdAndUserId(10, 1)).willReturn(Optional.empty());

assertThatThrownBy(() -> addressService.update(1, 10, request))
    .isInstanceOf(AddressNotFoundException.class);
```

존재 여부와 소유자 불일치를 모두 404로 처리해 다른 사용자의 addressId 존재를 노출하지 않는다.

- [ ] **Step 2: 기본 배송지 단일화 테스트**

새 주소 또는 수정 주소가 기본 배송지라면 같은 트랜잭션에서 `clearDefaultByUserId(userId)`를 먼저 실행한 후 대상 주소만 true로 저장한다.

- [ ] **Step 3: 서비스 구현**

쓰기 메서드에 `@Transactional`, 목록 조회에는 `@Transactional(readOnly = true)`를 사용한다. 기본 배송지 삭제 후 다른 주소를 자동 기본값으로 바꾸지 않는다.

- [ ] **Step 4: 서비스 테스트 실행**

```bash
./gradlew test --tests com.dbidding.user.service.AddressServiceTest
```

Expected: CRUD, 소유권, 기본 배송지 테스트 PASS.

### Task 4: Controller

**Files:**
- Create: `backend/src/main/java/com/dbidding/user/controller/AddressController.java`
- Test: `backend/src/test/java/com/dbidding/user/controller/AddressControllerTest.java`

**Interfaces:**
- Consumes: `@CurrentUser Integer userId`
- Produces: `/api/users/me/addresses`

- [ ] **Step 1: API 작성**

```java
@RestController
@RequestMapping("/api/users/me/addresses")
public class AddressController {
    @GetMapping
    public List<AddressResponse> getAll(@CurrentUser Integer userId) {
        return addressService.getAll(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@CurrentUser Integer userId, @Valid @RequestBody AddressRequest request) {
        return addressService.create(userId, request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(@CurrentUser Integer userId, @PathVariable Integer addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(userId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser Integer userId, @PathVariable Integer addressId) {
        addressService.delete(userId, addressId);
    }
}
```

- [ ] **Step 2: MockMvc 테스트**

`@CurrentUser`가 1을 주입하는 테스트 설정을 사용해 200, 201, 204, 400, 404 응답을 검증한다.

- [ ] **Step 3: 전체 테스트 및 커밋**

```bash
./gradlew clean test
git add backend/src/main/java/com/dbidding/user backend/src/test/java/com/dbidding/user
git commit -m "feat: 배송지 CRUD 구현"
```

## 완료 조건

- 모든 변경 쿼리가 현재 userId를 조건으로 사용한다.
- `defaultAddress=true`로 저장할 때 기존 기본 배송지를 해제한다.
- User Entity나 UserRepository를 직접 참조하지 않는다.
- Controller가 임의의 userId를 입력으로 받지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
