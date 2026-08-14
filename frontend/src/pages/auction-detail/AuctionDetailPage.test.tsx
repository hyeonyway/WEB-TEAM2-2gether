import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import {AuthContext} from '../../auth/AuthProvider';
import ToastContainer from '../../components/Toast';
import AuctionDetailPage from './AuctionDetailPage';

const currentUserId=vi.hoisted(()=>vi.fn());

vi.mock('../../auth/useCurrentUserId',()=>({useCurrentUserId:currentUserId}));

const apiMocks=vi.hoisted(()=>({
  detail:vi.fn(),
  bids:vi.fn(),
  bidContext:vi.fn(),
}));

vi.mock('../../api/auctionApi',async importOriginal=>({
  ...await importOriginal<typeof import('../../api/auctionApi')>(),
  fetchAuctionDetail:apiMocks.detail,
  fetchAuctionBids:apiMocks.bids,
  fetchAuctionBidContext:apiMocks.bidContext,
}));

const detail={
  id:10,
  card:{id:1,name:'피카츄',set_name:'151',psa_grade:'10',language:'JP',thumbnail_url:'/cards/pikachu.png'},
  seller:{id:2,nickname:'판매자',trade_count:3,trust_score:95},
  start_price:10000,current_price:12000,bid_increment:1000,minimum_bid:13000,bid_count:1,
  starts_at:'2026-08-01T10:00:00',ends_at:'2099-08-01T20:00:00',status:'OPEN' as const,
  my_bid_status:'NONE' as const,my_bid_amount:null,
  description:'상태 좋음',seller_memo:null,seller_grade:null,shipping_fee:3000,buy_now_price:20000,
  photos:[
    {id:11,url:'/uploads/front.png',order:0,representative:true},
    {id:12,url:'/uploads/back.png',order:1,representative:false},
  ],psa_certification:null,
};

function renderAnonymousDetail(status:'anonymous'|'authenticated'='anonymous'){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return render(<QueryClientProvider client={queryClient}>
    <MemoryRouter initialEntries={['/auction/10']}>
      <AuthContext.Provider value={{status,retryInitialization:vi.fn()}}>
        <Routes>
          <Route path="/auction/:auctionId" element={<AuctionDetailPage/>}/>
          <Route path="/cards/:cardId" element={<h1>카드 시세 Route</h1>}/>
        </Routes>
        <ToastContainer/>
      </AuthContext.Provider>
    </MemoryRouter>
  </QueryClientProvider>);
}

describe('AuctionDetailPage',()=>{
  beforeEach(()=>{
    currentUserId.mockReturnValue(null);
    apiMocks.detail.mockReset().mockResolvedValue(detail);
    apiMocks.bids.mockReset().mockResolvedValue({
      content:[{id:1,amount:12000,bidder_alias:'user-1***',is_highest:true,created_at:'2026-08-01T11:00:00'}],
      page:0,size:5,total_elements:1,has_next:false,
    });
    apiMocks.bidContext.mockReset().mockResolvedValue({});
  });

  it('내가 등록한 경매의 입찰 버튼을 비활성화한다',async()=>{
    currentUserId.mockReturnValue(2);
    apiMocks.bidContext.mockResolvedValue({
      auction_id:10,status:'OPEN',version:1,current_price:12000,minimum_bid:13000,bid_increment:1000,
      my_bid_status:'NONE',my_bid_amount:null,
      wallet:{available_balance:100000,frozen_balance:0},recent_bids:[],
    });

    renderAnonymousDetail('authenticated');

    expect(await screen.findByRole('button',{name:'내가 등록한 경매'})).toBeDisabled();
  });

  it('비로그인 상세에서 공개 정보와 입찰 이력만 조회한다',async()=>{
    renderAnonymousDetail();

    expect(await screen.findByRole('heading',{name:'피카츄'})).toBeInTheDocument();
    expect(screen.getAllByText('12,000원')).toHaveLength(2);
    expect(screen.queryByText('보유 포인트')).not.toBeInTheDocument();
    expect(apiMocks.bidContext).not.toHaveBeenCalled();
  });

  it('비로그인 사용자가 입찰하려면 로그인 안내를 표시한다',async()=>{
    renderAnonymousDetail();
    const user=userEvent.setup();

    await user.click(await screen.findByRole('button',{name:'13,000원부터 입찰하기'}));

    await waitFor(()=>expect(screen.getByText('로그인이 필요합니다')).toBeInTheDocument());
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('입찰 팝업을 열기 전에 최신 입찰 컨텍스트를 다시 조회한다',async()=>{
    apiMocks.bidContext.mockResolvedValue({
      auction_id:10,status:'OPEN',version:1,current_price:12000,minimum_bid:13000,bid_increment:1000,
      my_bid_status:'OUTBID',my_bid_amount:12000,
      wallet:{available_balance:100000,frozen_balance:0},recent_bids:[],
    });
    renderAnonymousDetail('authenticated');
    const user=userEvent.setup();

    await screen.findByRole('button',{name:'13,000원부터 입찰하기'});
    expect(apiMocks.bidContext).toHaveBeenCalledOnce();

    await user.click(screen.getByRole('button',{name:'13,000원부터 입찰하기'}));

    await waitFor(()=>expect(apiMocks.bidContext).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole('dialog',{name:'피카츄 경매 참여'})).toBeInTheDocument();
  });

  it('즉시 구매가가 없는 경매도 상세 정보를 표시한다',async()=>{
    apiMocks.detail.mockResolvedValue({...detail,buy_now_price:null});

    renderAnonymousDetail();

    expect(await screen.findByRole('heading',{name:'피카츄'})).toBeInTheDocument();
    expect(screen.getByText('즉시 구매가 없음')).toBeInTheDocument();
  });

  it('현재 입찰가 영역에 즉시 구매가를 함께 표시한다',async()=>{
    const{container}=renderAnonymousDetail();

    await screen.findByRole('heading',{name:'피카츄'});
    expect(container.querySelector('.auction-current-price .auction-buy-now-price'))
      .toHaveTextContent('즉시 구매가 20,000원');
  });

  it('상세 정보를 기다리는 동안 실제 레이아웃 스켈레톤을 표시한다',()=>{
    apiMocks.detail.mockReturnValue(new Promise(()=>{}));
    apiMocks.bids.mockReturnValue(new Promise(()=>{}));

    const{container}=renderAnonymousDetail();

    expect(screen.getByRole('status',{name:'경매 상세 정보를 불러오는 중'}))
      .toHaveAttribute('aria-busy','true');
    expect(container.querySelector('.auction-detail-skeleton-stage'))
      .toContainElement(container.querySelector('.auction-detail-skeleton-card'));
  });

  it('카드 이미지와 판매자 사진을 순서대로 넘겨 본다',async()=>{
    renderAnonymousDetail();
    const user=userEvent.setup();
    const image=await screen.findByRole('img',{name:'피카츄 이미지 1 / 3'});

    expect(image).toHaveAttribute('src','/cards/pikachu.png');
    await user.click(screen.getByRole('button',{name:'다음 이미지'}));
    expect(screen.getByRole('img',{name:'피카츄 이미지 2 / 3'}))
      .toHaveAttribute('src','/uploads/front.png');
    await user.click(screen.getByRole('button',{name:'3번째 이미지 보기'}));
    expect(screen.getByRole('img',{name:'피카츄 이미지 3 / 3'}))
      .toHaveAttribute('src','/uploads/back.png');
  });

  it('카드 대표 이미지만 있어도 하단 썸네일을 표시한다',async()=>{
    apiMocks.detail.mockResolvedValue({...detail,photos:[]});

    renderAnonymousDetail();

    expect(await screen.findByRole('img',{name:'피카츄 이미지 1 / 1'}))
      .toHaveAttribute('src','/cards/pikachu.png');
    expect(screen.getByRole('button',{name:'1번째 이미지 보기'}))
      .toHaveAttribute('aria-current','true');
    expect(screen.queryByRole('button',{name:'다음 이미지'})).not.toBeInTheDocument();
  });

  it('카드 시세 상세 링크는 해당 Card Route로 SPA 이동한다',async()=>{
    renderAnonymousDetail();
    const user=userEvent.setup();

    await user.click(await screen.findByRole('link',{name:'카드 시세 상세'}));

    expect(screen.getByRole('heading',{name:'카드 시세 Route'})).toBeInTheDocument();
  });
});
