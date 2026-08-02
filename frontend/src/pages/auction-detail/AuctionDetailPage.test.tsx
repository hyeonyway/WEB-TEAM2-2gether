import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import {AuthContext} from '../../auth/AuthProvider';
import ToastContainer from '../../components/Toast';
import AuctionDetailPage from './AuctionDetailPage';

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
  card:{id:1,name:'피카츄',set_name:'151',psa_grade:'10',language:'JP',thumbnail_url:null},
  seller:{id:2,nickname:'판매자',trade_count:3,trust_score:95},
  start_price:10000,current_price:12000,bid_increment:1000,minimum_bid:13000,bid_count:1,
  starts_at:'2026-08-01T10:00:00',ends_at:'2099-08-01T20:00:00',status:'OPEN' as const,
  my_bid_status:'NONE' as const,my_bid_amount:null,version:1,
  description:'상태 좋음',seller_memo:null,shipping_fee:3000,buy_now_price:20000,
  photos:[],psa_certification:null,
};

function renderAnonymousDetail(){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return render(<QueryClientProvider client={queryClient}>
    <MemoryRouter initialEntries={['/auction/10']}>
      <AuthContext.Provider value={{status:'anonymous',retryInitialization:vi.fn()}}>
        <Routes><Route path="/auction/:auctionId" element={<AuctionDetailPage/>}/></Routes>
        <ToastContainer/>
      </AuthContext.Provider>
    </MemoryRouter>
  </QueryClientProvider>);
}

describe('AuctionDetailPage',()=>{
  beforeEach(()=>{
    apiMocks.detail.mockReset().mockResolvedValue(detail);
    apiMocks.bids.mockReset().mockResolvedValue({
      content:[{id:1,amount:12000,bidder_alias:'user-1***',is_highest:true,created_at:'2026-08-01T11:00:00'}],
      page:0,size:5,total_elements:1,has_next:false,
    });
    apiMocks.bidContext.mockReset().mockResolvedValue({});
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
});
