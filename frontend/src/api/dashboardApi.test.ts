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
});
