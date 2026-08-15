import {describe,expect,it} from 'vitest';
import {notificationDedupKey} from './notificationKey';

describe('notificationDedupKey',()=>{
  it('OUTBID는 auctionId가 달라도 bidId가 같으면 같은 키를 반환한다',()=>{
    const a=notificationDedupKey({type:'OUTBID',auctionId:1,bidId:100});
    const b=notificationDedupKey({type:'OUTBID',auctionId:2,bidId:100});

    expect(a).toBe(b);
  });

  it('OUTBID는 bidId가 다르면 다른 키를 반환한다',()=>{
    const a=notificationDedupKey({type:'OUTBID',auctionId:1,bidId:100});
    const b=notificationDedupKey({type:'OUTBID',auctionId:1,bidId:200});

    expect(a).not.toBe(b);
  });

  it('OUTBID가 아닌 타입은 auctionId로 키를 만든다',()=>{
    const a=notificationDedupKey({type:'AUCTION_WON',auctionId:1,bidId:0});
    const b=notificationDedupKey({type:'AUCTION_WON',auctionId:2,bidId:0});

    expect(a).not.toBe(b);
  });

  it('타입이 다르면 같은 auctionId여도 다른 키를 반환한다',()=>{
    const a=notificationDedupKey({type:'AUCTION_WON',auctionId:1,bidId:0});
    const b=notificationDedupKey({type:'ORDER_COMPLETED',auctionId:1,bidId:0});

    expect(a).not.toBe(b);
  });
});
