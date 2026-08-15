import {beforeEach,describe,expect,it,vi} from 'vitest';
import {markNotificationAsRead} from './notificationApi';

describe('markNotificationAsRead',()=>{
  beforeEach(()=>{vi.restoreAllMocks();});

  it('OUTBID는 type/auctionId/bidId를 쿼리 파라미터로 보낸다',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(null,{status:204}));

    await markNotificationAsRead({type:'OUTBID',auctionId:10,bidId:5});

    const[url]=fetchMock.mock.calls[0]!;
    const requestUrl=new URL(String(url),'http://localhost');
    expect(requestUrl.pathname).toBe('/api/notifications/read');
    expect(requestUrl.searchParams.get('type')).toBe('OUTBID');
    expect(requestUrl.searchParams.get('auctionId')).toBe('10');
    expect(requestUrl.searchParams.get('bidId')).toBe('5');
  });

  it('OUTBID가 아닌 타입은 bidId 파라미터를 보내지 않는다',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(null,{status:204}));

    await markNotificationAsRead({type:'AUCTION_WON',auctionId:10,bidId:0});

    const[url]=fetchMock.mock.calls[0]!;
    const requestUrl=new URL(String(url),'http://localhost');
    expect(requestUrl.searchParams.get('type')).toBe('AUCTION_WON');
    expect(requestUrl.searchParams.get('auctionId')).toBe('10');
    expect(requestUrl.searchParams.has('bidId')).toBe(false);
  });
});
