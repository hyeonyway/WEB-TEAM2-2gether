package com.dbidding.notification.event;

// TODO: auction 담당(이은기)이 실제 이벤트 클래스를 auction/event에 만들면 그쪽 클래스로 교체하고 이 파일은 삭제.
// 이슈 #69에서 제안한 shape 기준 임시 계약. winnerId가 null이면 유찰로 취급한다.
public record AuctionClosedEvent(
        Integer auctionId,
        Integer cardId,
        String cardName,
        Integer winnerId,
        Integer sellerId,
        Long finalPrice
) {
}