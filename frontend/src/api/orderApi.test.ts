import {beforeEach,describe,expect,it,vi} from 'vitest';
import {setAccessToken,clearAccessToken} from './accessTokenStore';
import {fetchPurchaseOrders} from './orderApi';

describe('orderApi',()=>{
  beforeEach(()=>{vi.restoreAllMocks();clearAccessToken();});

  it('Redis pending 주문의 null ID와 Stream ID를 유지한다',async()=>{
    vi.stubEnv('VITE_API_PROFILE','redis');
    vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify([{
      id:null,auction_id:404,card_name:'리자몽',price:20_000,status:'PENDING_CONFIRM',
      created_at:'2026-08-13T00:00:00Z',stream_id:'1786551225357-0',
    }]),{status:200,headers:{'Content-Type':'application/json'}}));
    setAccessToken('order-access-token');

    await expect(fetchPurchaseOrders()).resolves.toEqual([{
      id:null,auctionId:404,cardName:'리자몽',price:20_000,status:'PENDING_CONFIRM',
      createdAt:'2026-08-13T00:00:00Z',streamId:'1786551225357-0',projectionStatus:'PENDING',
    }]);
    vi.unstubAllEnvs();
  });
});
