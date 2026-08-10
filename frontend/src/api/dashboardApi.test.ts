import {beforeEach,describe,expect,it,vi} from 'vitest';
import {clearAccessToken,setAccessToken} from './accessTokenStore';
import {fetchParticipatingAuctions,fetchRecentWins} from './dashboardApi';

function jsonResponse(body:unknown){
  return new Response(JSON.stringify(body),{
    status:200,
    headers:{'Content-Type':'application/json'},
  });
}

describe('dashboardApi',()=>{
  beforeEach(()=>{
    vi.restoreAllMocks();
    clearAccessToken();
  });

  it.each([
    ['참여 중인 경매',()=>fetchParticipatingAuctions('ENDING_SOON'),'/api/dashboard/participating-auctions?sort=ENDING_SOON'],
    ['최근 낙찰',()=>fetchRecentWins('LATEST'),'/api/dashboard/recent-wins?sort=LATEST'],
  ])('%s를 현재 Access Token으로 조회한다',async(_name,fetchDashboard,path)=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(jsonResponse([]));
    setAccessToken('dashboard-access-token');

    await expect(fetchDashboard()).resolves.toEqual([]);

    expect(String(fetchMock.mock.calls[0]?.[0]).endsWith(path)).toBe(true);
    const requestOptions=fetchMock.mock.calls[0]?.[1];
    expect(new Headers(requestOptions?.headers).get('Authorization'))
      .toBe('Bearer dashboard-access-token');
  });

  it('대시보드 경매의 판매자 ID를 입찰 목록 모델에 보존한다',async()=>{
    vi.spyOn(globalThis,'fetch').mockResolvedValue(jsonResponse([{
      id:1,
      seller_id:7,
      card:{id:2,name:'피카츄',psa_grade:'10',language:'KR',thumbnail_url:null},
      start_price:10_000,current_price:12_000,bid_increment:1_000,bid_count:2,
      ends_at:'2026-08-07T12:00:00Z',status:'OPEN',version:1,
      my_bid_status:'LEADING',my_bid_amount:12_000,
    }]));
    setAccessToken('dashboard-access-token');

    const [auction]=await fetchParticipatingAuctions('ENDING_SOON');

    expect(auction.sellerId).toBe(7);
  });
});
