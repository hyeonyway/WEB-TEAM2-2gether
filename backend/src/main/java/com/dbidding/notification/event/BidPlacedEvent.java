package com.dbidding.notification.event;

// TODO: auction 담당(이은기)이 실제 이벤트 클래스를 auction/event에 만들면 그쪽 클래스로 교체하고 이 파일은 삭제.
// 이슈 #69에서 제안한 shape를 갱신한 임시 계약 - 별도 outbid 이벤트 없이 previousBidderId로 상회 입찰을 표현한다.
// previousBidderId가 null이면 최초 입찰(상회 입찰 알림 대상 없음).
public record BidPlacedEvent(
        Integer auctionId,
        Integer cardId,
        String cardName,
        Integer bidderId,
        Integer previousBidderId
) {
}
