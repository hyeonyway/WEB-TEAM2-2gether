import {render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,useLocation} from 'react-router-dom';
import {describe,expect,it,vi} from 'vitest';
import {AuthContext} from '../../../auth/AuthProvider';
import ToastContainer from '../../../components/Toast';
import type {AuctionDto} from '../../../dto/auctionDto';
import AuctionCatalog from './AuctionCatalog';

const currentUserId=vi.hoisted(()=>vi.fn());

vi.mock('../../../auth/useCurrentUserId',()=>({useCurrentUserId:currentUserId}));

const auction:AuctionDto={
  id:1,
  card:{
    id:1,name:'피카츄',marketPrice:10000,lowPrice:9000,highPrice:11000,changeRate:0,
    theme:'gold',bidCount:0,psaGrade:'10',language:'JP',imageUrl:null,
  },
  startPrice:10000,currentPrice:12000,bidIncrement:1000,bidCount:1,
  endsAt:'2099-08-03T12:00:00',status:'OPEN',myBidStatus:'NONE',myBidAmount:null,
};

function LocationProbe(){
  const location=useLocation();
  return <output data-testid="auction-catalog-path">{location.pathname}</output>;
}

function renderCatalog(status:'anonymous'|'authenticated'='anonymous',auctionOverride:Partial<AuctionDto>={}){
  return render(<MemoryRouter>
    <AuthContext.Provider value={{status,role:null,retryInitialization:vi.fn()}}>
      <AuctionCatalog auctions={[{...auction,...auctionOverride}]}/>
      <ToastContainer/>
      <LocationProbe/>
    </AuthContext.Provider>
  </MemoryRouter>);
}

describe('AuctionCatalog',()=>{
  it('내가 등록한 경매는 입찰 버튼을 비활성화한다',()=>{
    currentUserId.mockReturnValue(7);

    renderCatalog('authenticated',{sellerId:7});

    expect(screen.getByRole('button',{name:'내가 등록한 경매'})).toBeDisabled();
  });

  it('현재 최고 입찰자는 입찰 현황을 보기 위해 버튼을 누를 수 있다',()=>{
    renderCatalog('authenticated',{myBidStatus:'LEADING',myBidAmount:12_000});

    const button=screen.getByRole('button',{name:/내가 최고가 입찰 중/});
    expect(button).toBeEnabled();
  });

  it('PSA 접두사가 포함된 등급도 한 번만 표시한다',()=>{
    render(<MemoryRouter><AuthContext.Provider value={{status:'anonymous',role:null,retryInitialization:vi.fn()}}>
      <AuctionCatalog auctions={[{...auction,card:{...auction.card,psaGrade:'PSA 10'}}]}/>
    </AuthContext.Provider></MemoryRouter>);

    expect(screen.getByText('PSA 10')).toBeInTheDocument();
    expect(screen.queryByText('PSA PSA 10')).not.toBeInTheDocument();
  });

  it('비로그인 사용자가 입찰을 누르면 다이얼로그 대신 로그인 안내를 표시한다',async()=>{
    const user=userEvent.setup();
    renderCatalog();

    await user.click(screen.getByRole('button',{name:'입찰하기'}));

    expect(screen.getByText('로그인이 필요합니다')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('상세보기는 경매 상세 Route로 SPA 이동한다',async()=>{
    const user=userEvent.setup();
    renderCatalog();

    await user.click(screen.getByRole('link',{name:'상세보기'}));

    expect(screen.getByTestId('auction-catalog-path')).toHaveTextContent('/auction/1');
  });

  it('경매 ID 목록이 같으면 이벤트 데이터가 바뀌어도 viewport observer를 다시 만들지 않는다',()=>{
    const disconnect=vi.fn();
    const Observer=vi.fn(function(){return {observe:vi.fn(),disconnect,unobserve:vi.fn()};});
    vi.stubGlobal('IntersectionObserver',Observer);
    const onSubscriptionAuctionIdsChange=vi.fn();
    const {rerender}=render(<MemoryRouter><AuthContext.Provider value={{status:'anonymous',role:null,retryInitialization:vi.fn()}}>
      <AuctionCatalog auctions={[auction]} onSubscriptionAuctionIdsChange={onSubscriptionAuctionIdsChange}/>
    </AuthContext.Provider></MemoryRouter>);

    rerender(<MemoryRouter><AuthContext.Provider value={{status:'anonymous',role:null,retryInitialization:vi.fn()}}>
      <AuctionCatalog auctions={[{...auction,bidCount:auction.bidCount+1}]} onSubscriptionAuctionIdsChange={onSubscriptionAuctionIdsChange}/>
    </AuthContext.Provider></MemoryRouter>);

    expect(Observer).toHaveBeenCalledTimes(1);
    expect(disconnect).not.toHaveBeenCalled();
  });
});
