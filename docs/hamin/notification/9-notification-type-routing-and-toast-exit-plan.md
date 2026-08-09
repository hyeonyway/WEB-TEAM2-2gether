# 알림 타입별 클릭 이동 분기 + 토스트 사라짐 애니메이션 계획

담당: 임하민. 이슈: [#224](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/224)
(브랜치 `feature/224-notification-type-routing` 예정).

## 배경

알림(벨 드롭다운/SSE 토스트) 클릭 시 무조건 `/auction/{auctionId}`로 이동한다.
`ORDER_COMPLETED`/`ORDER_CANCELLED`도 마찬가지라 주문 관련 알림을 클릭해도 실제로 주문을
확인/처리할 수 있는 대시보드 "주문" 탭으로 못 간다. 근본 원인은 백엔드 `NotificationResponse`/
프론트 `NotificationDto`에 `type` 필드가 없어서 프론트가 알림 종류를 구분할 방법이 없다는
것이다.

이슈 본문의 OUTBID 구조화 필드 항목(카드이름을 메시지 문자열이 아닌 별도 필드로 내려줄지)은
**사용자 지시로 이번 스코프에서 제외**하고 별도로 나중에 처리한다.

## 현재 상태 확인

- `NotificationType` enum(`AUCTION_OPENED, OUTBID, AUCTION_WON, AUCTION_UNSOLD,
  ORDER_COMPLETED, ORDER_CANCELLED`)과 `Notification` 엔티티의 `type` 필드는 이미 존재하고
  저장도 되고 있다(`@Enumerated(EnumType.STRING)`). `NotificationResponse.from()`이 이걸
  응답에 노출만 안 시키고 있을 뿐이라 엔티티/DB 변경은 필요 없다.
- 백엔드는 전역 snake_case 네이밍 전략을 쓰지 않는다(`application.yml`에 Jackson 네이밍
  전략 설정 없음) — `NotificationResponse`의 기존 필드(`auctionId`, `isRead`)도 그대로
  camelCase로 나간다. 새 `type` 필드도 별도 `@JsonProperty` 없이 camelCase로 추가하면 되고,
  enum은 Jackson 기본 직렬화로 `"ORDER_COMPLETED"` 같은 문자열이 된다.
- `DashboardPage.tsx`의 `active` 탭 상태는 순수 `useState`이고 `useSearchParams`/
  `useLocation`을 전혀 안 써서 URL만으로 특정 탭을 열 수 없다.
- `useNotificationToasts.ts`의 `dismiss`는 `setToasts` filter로 배열에서 즉시 제거한다 —
  중간 애니메이션 상태 없음.

## 1. Backend

### `NotificationResponse.java`

`type` 필드를 추가하고 `notification.getType()`을 매핑한다.

```java
public record NotificationResponse(
        Long id,
        Integer auctionId,
        NotificationType type,
        String message,
        boolean isRead,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAuctionId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
```

### 테스트

`NotificationControllerTest`의 기존 `given(...).willReturn(new NotificationPage(List.of(
Notification.of(1, 10, NotificationType.AUCTION_OPENED, "메시지1")), ...))` 케이스들은 이미
`NotificationType`을 넘기고 있으니 코드 변경은 필요 없고, `jsonPath("$.items[*].type")` 검증을
`알림_목록을_조회하면_200과_아이템_및_페이지_정보를_반환한다` 케이스에 한 줄 추가한다
(`"$.items[0].type"` → `"AUCTION_OPENED"`).

## 2. Frontend

### `dto/notificationDto.ts`

```ts
export type NotificationType=
  |'AUCTION_OPENED'|'OUTBID'|'AUCTION_WON'|'AUCTION_UNSOLD'|'ORDER_COMPLETED'|'ORDER_CANCELLED';

export type NotificationDto={
  id:number;
  auctionId:number;
  type:NotificationType;
  message:string;
  isRead:boolean;
  createdAt:string;
};
```

### `api/notificationApi.ts`

`MOCK_SEED`의 각 항목에 `type`을 채운다(실제 알림 성격에 맞춰서 — 예: 낙찰 알림은
`AUCTION_WON`, 상회입찰은 `OUTBID`, 찜한 카드 관련은 `AUCTION_OPENED`). 목 모드에서도 새
분기 로직을 확인할 수 있게 하기 위함.

### 새 유틸: `utils/notificationNavigation.ts`

`NotificationBell`과 `NotificationToastStack` 양쪽에서 동일한 분기 로직이 필요하므로 중복
대신 공유 헬퍼로 뺀다.

```ts
import type {NotificationDto} from '../dto/notificationDto';

const ORDER_TAB_TYPES=new Set(['ORDER_COMPLETED','ORDER_CANCELLED']);

export function getNotificationPath(notification:NotificationDto):string{
  return ORDER_TAB_TYPES.has(notification.type)
    ? '/dashboard?tab=orders'
    : `/auction/${notification.auctionId}`;
}
```

단위 테스트(`utils/notificationNavigation.test.ts`)로 두 분기(주문류/그 외)를 검증한다.

### `NotificationBell.tsx`

```ts
const handleNavigate=(notification:NotificationDto)=>{
  markAsRead(notification.id);
  setIsOpen(false);
  navigate(getNotificationPath(notification));
};
```

### `NotificationToastStack.tsx`

```ts
const handleOpen=(notification:NotificationDto)=>{
  markAsReadMutation.mutate(notification.id);
  onDismiss(notification.id);
  navigate(getNotificationPath(notification));
};
```

### `DashboardPage.tsx` — `?tab=` 딥링크

`react-router-dom`의 `useSearchParams`로 초기 탭을 URL에서 읽고, 탭 전환 시 URL도 같이
갱신한다(새로고침/뒤로가기에도 탭 유지, `replace`로 히스토리 오염 방지).

```tsx
const[searchParams,setSearchParams]=useSearchParams();
const tabParam=searchParams.get('tab');
const initialTab=sections.some(([id])=>id===tabParam)?tabParam as SectionId:'participating';
const[active,setActiveState]=useState<SectionId>(initialTab);

const setActive=(id:SectionId)=>{
  setActiveState(id);
  setSearchParams(id==='participating'?{}:{tab:id},{replace:true});
};
```

탭 버튼의 `onClick={()=>setActive(id)}`는 그대로 두되(이미 지역 `setActive`를 부르므로),
위처럼 `setActive`를 감싸서 URL 동기화까지 하나의 함수로 처리한다. `participating`은 기본값이라
쿼리 파라미터를 아예 안 붙인다(URL이 깔끔하게 `/dashboard`로 남음).

## 3. 사라짐 애니메이션 (`useNotificationToasts` + `NotificationToastStack`)

### `useNotificationToasts.ts`

각 토스트에 `isDismissing` 플래그를 추가해 2단계로 제거한다: ① `dismiss` 호출 시 배열에서
바로 안 지우고 `isDismissing:true`로 마킹 + 기존 자동소멸 타이머 취소, ② 애니메이션 길이
(`DISMISS_ANIMATION_MS`, CSS와 맞춰 200ms)만큼 뒤에 실제로 배열에서 제거.

```ts
const AUTO_DISMISS_MS=30_000;
const DISMISS_ANIMATION_MS=200;

type ToastState=NotificationDto&{isDismissing:boolean};

export function useNotificationToasts(){
  const[toasts,setToasts]=useState<ToastState[]>([]);
  const timersRef=useRef(new Map<number,ReturnType<typeof setTimeout>>());

  const remove=useCallback((id:number)=>{
    setToasts(current=>current.filter(toast=>toast.id!==id));
    timersRef.current.delete(id);
  },[]);

  const dismiss=useCallback((id:number)=>{
    const timer=timersRef.current.get(id);
    if(timer)clearTimeout(timer);
    setToasts(current=>current.map(toast=>toast.id===id?{...toast,isDismissing:true}:toast));
    timersRef.current.set(id,setTimeout(()=>remove(id),DISMISS_ANIMATION_MS));
  },[remove]);

  const push=useCallback((notification:NotificationDto)=>{
    setToasts(current=>current.some(toast=>toast.id===notification.id)
      ?current
      :[...current,{...notification,isDismissing:false}]);
    timersRef.current.set(notification.id,setTimeout(()=>dismiss(notification.id),AUTO_DISMISS_MS));
  },[dismiss]);

  const clear=useCallback(()=>{
    timersRef.current.forEach(timer=>clearTimeout(timer));
    timersRef.current.clear();
    setToasts([]);
  },[]);

  useEffect(()=>{
    const timers=timersRef.current;
    return()=>{
      timers.forEach(timer=>clearTimeout(timer));
      timers.clear();
    };
  },[]);

  return {toasts,push,dismiss,clear};
}
```

`push`가 새 타이머를 세팅하기 전에 `dismiss`가 이미 같은 id로 애니메이션 타이머를 걸어둔
경우는 없다(같은 id가 다시 push되는 건 아직 배열에 남아있을 때뿐이고, 그때는 `some` 체크로
아예 push를 건너뛴다).

### `NotificationToastStack.tsx`

`toast.isDismissing`일 때 `dismissing` 클래스를 추가한다.

```tsx
<div key={notification.id} className={`notification-toast${notification.isDismissing?' dismissing':''}`}>
```

닫기 버튼/본문 클릭 시에도 `onDismiss(id)`를 호출하는 기존 흐름은 그대로 유지 — 이제
`onDismiss`(=`dismiss`)가 애니메이션을 거쳐서 지우는 것으로 동작이 바뀔 뿐 호출 시그니처는
동일하다.

### `tailwind.css`

기존 `.toast`(다른 토스트 시스템)의 `toast-in-out` 키프레임 선례를 따라 사라지는 애니메이션을
추가한다.

```css
.notification-toast.dismissing{animation:notification-toast-out .2s ease forwards}
@keyframes notification-toast-out{
  0%{opacity:1;transform:translateX(0)}
  100%{opacity:0;transform:translateX(8px)}
}
```

## 4. 테스트

- `NotificationControllerTest` — 위 1절 대로 `type` JSON 필드 검증 한 줄 추가.
- `utils/notificationNavigation.test.ts`(신규) — `ORDER_COMPLETED`/`ORDER_CANCELLED`는
  `/dashboard?tab=orders`, 그 외 타입은 `/auction/{auctionId}`를 반환하는지.
- `NotificationBell.test.tsx` — 기존 테스트 픽스처에 `type` 필드 추가(타입 에러 방지),
  "이동" 버튼 클릭 시 `ORDER_COMPLETED` 알림은 `/dashboard?tab=orders`로, 그 외 알림은
  `/auction/{auctionId}`로 이동하는지 `LocationProbe` 패턴(`NotificationToastStack.test.tsx`
  선례)으로 검증하는 케이스 추가.
- `NotificationToastStack.test.tsx` — 픽스처에 `type` 추가, 주문류 알림 클릭 시
  `/dashboard?tab=orders`로 이동하는 케이스 추가.
- `useNotificationToasts.test.tsx` — 동작이 바뀌는 기존 케이스들 갱신:
  - "dismiss하면 해당 알림만 제거되고 나머지는 남는다" → dismiss 직후엔 `isDismissing:true`로
    남아있고, `DISMISS_ANIMATION_MS`만큼 지나야 배열에서 실제로 빠지는 것으로 어서션 변경.
  - "30초가 지나면 자동으로 사라진다" → `30_000+DISMISS_ANIMATION_MS`만큼 advance해야 실제
    제거 확인 가능하도록 변경.
  - 나머지(중복 push 방지, clear 즉시 제거 등)는 그대로.
- `DashboardPage` 관련 컴포넌트 테스트는 기존에 선례가 없어(233 계획 문서에서도 `OrdersPanel`
  컴포넌트 테스트를 안 만들었음) 이번에도 추가하지 않고 브라우저에서 직접
  `/dashboard?tab=orders` 진입 확인 + 알림 클릭 흐름을 눈으로 검증한다.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
