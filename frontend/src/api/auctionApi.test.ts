import {beforeEach,describe,expect,it,vi} from 'vitest';
import {clearAccessToken,setAccessToken} from './accessTokenStore';
import {createAuctionBid,fetchAuctionBidContext,fetchAuctionBids,fetchAuctionDetail,fetchAuctions,fetchFailedAuctions} from './auctionApi';

const auctionResponse={
  id:10,
  card:{
    id:1,
    name:'피카츄',
    set_name:'포켓몬 카드',
    psa_grade:'PSA 10',
    language:'Japanese',
    thumbnail_url:'pokemon-cards/pikachu.webp',
  },
  seller:{id:2,nickname:'판매자',trade_count:0,trust_score:0},
  start_price:10000,
  current_price:12000,
  bid_increment:1000,
  minimum_bid:13000,
  bid_count:2,
  starts_at:'2026-07-31T10:00:00',
  ends_at:'2026-07-31T20:00:00',
  status:'OPEN',
  my_bid_status:'NONE',
  my_bid_amount:null,
  version:1,
};

function jsonResponse(body:unknown,status=200){
  return new Response(JSON.stringify(body),{
    status,
    headers:{'Content-Type':'application/json'},
  });
}

describe('auctionApi',()=>{
  beforeEach(()=>{
    vi.restoreAllMocks();
    clearAccessToken();
    setAccessToken('auction-access-token');
  });

  it('인증 헤더 없이 경매 목록을 cursor로 조회한다',async()=>{
    clearAccessToken();
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(jsonResponse({
      content:[auctionResponse],
      next_cursor:'next-token',
      has_next:true,
    }));

    const auctions=await fetchAuctions({
      keyword:'',
      psaGrade:null,
      sort:'BID_COUNT',
      size:12,
    },'current-token');

    expect(auctions.content).toHaveLength(1);
    expect(auctions).toMatchObject({
      next_cursor:'next-token',
      has_next:true,
    });
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('cursor=current-token');
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('size=12');
    const headers=new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.get('Authorization')).toBeNull();
  });

  it('JWT와 멱등성 키로 입찰한다',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(jsonResponse({
      bid:{id:1,amount:13000,status:'LEADING',created_at:'2026-07-31T11:00:00'},
      auction:{id:10,version:2,current_price:13000,minimum_bid:14000,bid_count:3,ends_at:'2026-07-31T20:00:00'},
      wallet:{available_balance:87000,frozen_balance:13000},
    },201));

    await createAuctionBid(10,13000,'bid-key');

    const [,options]=fetchMock.mock.calls[0];
    const headers=new Headers(options?.headers);
    expect(headers.get('Authorization')).toBe('Bearer auction-access-token');
    expect(headers.get('Idempotency-Key')).toBe('bid-key');
  });

  it('JWT로 판매자 본인의 유찰 경매 목록을 조회한다',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(jsonResponse([
      {id:1,card_name:'리자몽',start_price:42000,closed_at:'2026-07-31T03:00:00Z'},
    ]));

    const failedAuctions=await fetchFailedAuctions();

    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/auctions/mine/failed');
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization'))
      .toBe('Bearer auction-access-token');
    expect(failedAuctions).toEqual([
      {id:1,cardName:'리자몽',startPrice:42000,closedAt:'2026-07-31T03:00:00Z'},
    ]);
  });

  it('경매 상세와 입찰 이력은 공개로, 입찰 컨텍스트는 JWT로 조회한다',async()=>{
    clearAccessToken();
    const fetchMock=vi.spyOn(globalThis,'fetch')
      .mockResolvedValueOnce(jsonResponse({
        ...auctionResponse,
        description:'설명',
        seller_memo:null,
        shipping_fee:3000,
        buy_now_price:20000,
        photos:[],
        psa_certification:null,
      }))
      .mockResolvedValueOnce(jsonResponse({
        content:[],page:0,size:5,total_elements:0,has_next:false,
      }))
      .mockResolvedValueOnce(jsonResponse({
        auction_id:10,
        status:'OPEN',
        version:1,
        current_price:12000,
        minimum_bid:13000,
        bid_increment:1000,
        my_bid_status:'NONE',
        my_bid_amount:null,
        wallet:{available_balance:100000,frozen_balance:0},
        recent_bids:[],
      }));

    await fetchAuctionDetail(10);
    await fetchAuctionBids(10);
    setAccessToken('auction-access-token');
    await fetchAuctionBidContext(10);

    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization')).toBeNull();
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('Authorization')).toBeNull();
    expect(new Headers(fetchMock.mock.calls[2]?.[1]?.headers).get('Authorization'))
      .toBe('Bearer auction-access-token');
  });
});
