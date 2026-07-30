package com.dbidding.notification.event;

// TODO: auction/bid 담당(이은기)이 실제 이벤트 클래스를 auction/event에 만들면 그쪽 클래스로 교체하고 이 파일은 삭제.
// 이슈 #69에서 제안한 shape 기준 임시 계약.
public record BidOutbidEvent(Integer auctionId, Integer cardId, String cardName, Integer previousBidderId) {
}