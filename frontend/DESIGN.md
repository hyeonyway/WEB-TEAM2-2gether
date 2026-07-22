---
id: kream
name: KREAM
country: KR
category: ecommerce-auction
primary_color: "#000000"
verified: "2026-07-13"
source: "/Users/admin/Downloads/DESIGN (1).md"
---

# KREAM 카드 경매 디자인 원칙

이 문서는 KREAM 카드 경매 서비스 UI의 공통 기준이다. KREAM의 화이트·차콜 커머스 화면을 기반으로 하되, 이 프로젝트에서는 즉시 구매보다 카드 경매와 입찰 대응을 우선한다.

## 1. Visual theme

- 캔버스는 흰색을 기본으로 사용한다.
- 텍스트와 주요 외곽선은 차콜 `#222222`을 사용한다.
- 보조 표면은 `#f5f5f5`, hairline은 `#f0f0f0`을 사용한다.
- 넓은 색상 체계보다 카드 이미지, 현재 경매가, 입찰 상태, 남은 시간이 시선을 이끌도록 한다.
- 상품 콘텐츠의 초록·빨강은 임의의 성공/실패 의미로 확장하지 않는다. 이 프로젝트에서 사용할 경우 상승 입찰과 경매 상태에만 제한한다.

## 2. Design tokens

### Colors

| Token | Value | Use |
| --- | --- | --- |
| `canvas` | `#ffffff` | 페이지 배경 |
| `primary` | `#222222` | 본문, 제목, 주요 테두리 |
| `surface` | `#f5f5f5` | 섹션 및 보조 배경 |
| `muted` | `#4e4e4e` | 필터·보조 텍스트 |
| `hairline` | `#f0f0f0` | 1px 구분선 |
| `on-primary` | `#ffffff` | 차콜 버튼 위 텍스트 |
| `bid-up` | `#f15746` | 상승 입찰 강조 |
| `bid-positive` | `#00a854` | 진행·잔여 상태 |

### Typography

- UI family: `Pretendard Variable`
- Fallback: `Pretendard, -apple-system, "system-ui", system-ui, Roboto, "Helvetica Neue", "Segoe UI", "Apple SD Gothic Neo", "Noto Sans KR", "Malgun Gothic", sans-serif`
- 기본 본문: `16px / 400`
- 보조 유틸리티: `13px / 400`
- 검색 입력: `24px / 700`
- 활성 탭: `16px / 700`
- 프로젝트의 일반 제목은 과도하게 무겁지 않게 `600`, 버튼은 `500`을 기본으로 한다.

### Spacing and shape

| Token | Value |
| --- | --- |
| `xxs` | `2px` |
| `xs` | `4px` |
| `sm` | `6px` |
| `md` | `8px` |
| `lg` | `12px` |
| `xl` | `24px` |
| `radius-none` | `0px` |
| `radius-sm` | `6px` |
| `radius-recovery` | `8px` |
| `radius-panel` | `16px` |
| `radius-pill` | `30px` |
| `shadow-none` | `none` |

모서리는 전역적으로 둥글게 만들지 않는다. 필터는 pill 또는 6px 사각형, 카드·패널은 화면 맥락에 따라 6~16px을 선택한다.

## 3. Auction product rules

이 프로젝트는 카드 시세 조회가 아니라 카드 경매 서비스다.

- `카드 시세` 대신 `카드 경매`, `현재 경매가`, `입찰가 추이`를 사용한다.
- 가격 그래프에는 하락 구간을 표현하지 않는다. 가격 유지 또는 신규 입찰에 따른 상승만 표현한다.
- 모든 카드 뷰에는 남은 경매 시간과 `입찰하기` 버튼을 제공한다.
- 경매 종료 시 시간은 `경매 종료`로 바뀌고 입찰 버튼은 비활성화한다.
- 상세 화면의 핵심 CTA는 `입찰하기`이며 판매 CTA는 노출하지 않는다.
- 입찰 팝업에는 전자지갑 포인트, 현재 경매가, 최소 입찰가, 선택 금액, 입찰 후 잔여 포인트, 포인트 부족 상태를 포함한다.
- 카드 클릭은 상세 경매 팝업을 열고, 입찰 버튼 클릭은 상세 팝업을 거치지 않고 입찰 팝업을 연다.

## 4. Components

### Filter pill

- Background `#f4f4f4`
- Text `#4e4e4e`
- Radius `30px`
- Height `30px`
- Padding `0 8px`
- Font `13px / 400`

### Outlined filter

- Background `#ffffff`
- Border `1px solid #f0f0f0`
- Radius `6px`
- Height `30px`
- Padding `0 6px 0 4px`
- Font `13px / 400`

### Navigation tab

- Height `44px`, padding `13px 0`
- Default font `16px / 400`
- Active state: `#222222`, `700`, bottom border `2px solid #222222`

### Auction card

- Product image, grade, card name, current auction price, rising percentage, remaining time, bid trend, total bids, and `입찰하기` button in that order.
- Card image and price are the primary visual hierarchy.
- The remaining time uses tabular numerals and updates every second.
- Use a calm white surface with a subtle hairline; avoid heavy shadows.

### Auction ticker

- Label the module `LIVE 경매 요약`.
- Show at least card name, current auction price, remaining time, and state.
- Pause continuous scrolling on hover where supported.
- State labels: `최고 입찰`, `상회 필요`, `종료 임박`, `종료`.

### Bid dialog

- Backdrop uses a neutral dark scrim.
- The dialog is a centered panel on desktop and a bottom sheet on mobile.
- Keep wallet balance and post-bid balance visible together.
- Disable submission when the selected bid exceeds wallet points.

## 5. Layout

- Desktop content uses a centered max-width container.
- Main card discovery uses a three-column grid and collapses to one column on narrow screens.
- My Page uses a left navigation rail on desktop and a horizontal navigation strip on mobile.
- Keep primary controls close to the content they affect; do not move auction actions into a distant global navigation.

## 6. Responsive behavior

The original collector evidence is desktop-first and does not establish a universal breakpoint. This project uses `760px` as the practical mobile breakpoint.

- At mobile widths, grids collapse to one column.
- Header actions remain visible but compact.
- Long filter rows scroll horizontally.
- Bid dialog becomes a full-width bottom sheet.
- Do not introduce a mobile interaction state that is not required by the corresponding component behavior.

## 7. Voice and microcopy

Use concise, operational Korean copy that explains the auction step or condition.

Do:

- `현재 경매가`
- `최소 입찰가`
- `전자지갑 포인트`
- `입찰 후 잔여 포인트`
- `경매 종료`
- `지금 상회 입찰`

Don't:

- Use `시세` for auction UI.
- Use `구매하기` or `판매` where the current flow is an auction bid.
- Imply a price decrease in an auction-only graph.
- Add unverified KREAM hover, loading, or checkout states as if they were official tokens.

## 8. Provenance

The base KREAM rules were extracted from the supplied `DESIGN (1).md`, which references KREAM home, shop, search, buying FAQ, authentication policy, and Pretendard documentation. Project-specific auction rules above are implementation decisions for this repository and should take precedence over generic marketplace assumptions.

Original reference: `/Users/admin/Downloads/DESIGN (1).md`
