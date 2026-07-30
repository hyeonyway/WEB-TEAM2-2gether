package com.dbidding.notification.event;

// TODO: auction 담당(이은기)이 실제 이벤트 클래스를 auction/event에 만들면 그쪽 클래스로 교체하고 이 파일은 삭제.
// 이슈 #69에서 제안한 shape 기준 임시 계약 - cardName을 포함해 notification이 card를 다시 조회하지 않게 한다.
public record AuctionCreatedEvent(Integer auctionId, Integer cardId, String cardName, Integer sellerId) {
}
