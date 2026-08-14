import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import type {AuctionDto,BidContextResponseDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {dashboardQueryKey} from '../queries/dashboardQueries';
import AuctionBidDialog from './AuctionBidDialog';
import ToastContainer from './Toast';

const mocks=vi.hoisted(()=>({
  fetchContext:vi.fn(),
  createBid:vi.fn(),
  streamHandler:null as ((event:AuctionStreamPayload)=>void)|null,
}));

vi.mock('../api/auctionApi',async importOriginal=>({
  ...await importOriginal<typeof import('../api/auctionApi')>(),
  fetchAuctionBidContext:mocks.fetchContext,
  createAuctionBid:mocks.createBid,
}));

vi.mock('../hooks/useAuctionStream',()=>({
  useAuctionStream:({onAuctionUpdated}:{onAuctionUpdated:(event:AuctionStreamPayload)=>void})=>{
    mocks.streamHandler=onAuctionUpdated;
  },
}));

const auction:AuctionDto={
  id:1,
  card:{id:1,name:'피카츄',marketPrice:10_000,lowPrice:10_000,highPrice:10_000,changeRate:0,theme:'gold',bidCount:1,psaGrade:'10',language:'KR',imageUrl:null},
  startPrice:9_000,currentPrice:10_000,bidIncrement:1_000,bidCount:1,buyNowPrice:20_000,
  endsAt:'2099-08-04T10:00:00Z',status:'OPEN',myBidStatus:'NONE',myBidAmount:null,
};

const context:BidContextResponseDto={
  auction_id:1,status:'OPEN',current_price:10_000,minimum_bid:11_000,bid_increment:1_000,
  my_bid_status:'NONE',my_bid_amount:null,wallet:{available_balance:100_000,frozen_balance:0},recent_bids:[],
};

const bidEvent:AuctionStreamPayload={
  type:'BID_PLACED',auction_id:1,bidder_id:2,previous_bidder_id:null,
  start_price:9_000,current_price:30_000,bid_increment:2_000,bid_count:2,
  ends_at:'2099-08-04T11:00:00Z',status:'OPEN',event_id:2,occurred_at:'2026-08-04T01:00:00Z',
};

function renderDialog(){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return {queryClient,...render(<QueryClientProvider client={queryClient}>
    <AuctionBidDialog auction={auction} onClose={vi.fn()}/><ToastContainer/>
  </QueryClientProvider>)};
}

describe('AuctionBidDialog',()=>{
  beforeEach(()=>{
    mocks.fetchContext.mockReset().mockResolvedValue(context);
    mocks.createBid.mockReset();
    mocks.streamHandler=null;
    vi.stubGlobal('matchMedia',vi.fn().mockReturnValue({matches:true}));
  });

  it('SSE 최소 입찰가가 올라가도 직접 입력한 금액은 유지하고 제출을 막는다',async()=>{
    renderDialog();
    const user=userEvent.setup();
    const input=await screen.findByRole('spinbutton');
    await screen.findByText('100,000P');
    expect(input).toHaveValue(11_000);

    await user.clear(input);
    await user.type(input,'25000');
    expect(screen.getByRole('button',{name:'25,000원 입찰하기'})).toBeInTheDocument();

    act(()=>mocks.streamHandler?.(bidEvent));

    await waitFor(()=>expect(input).toHaveValue(25_000));
    expect(screen.getByRole('button',{name:'25,000원 입찰하기'})).toBeDisabled();
    expect(screen.getByText('최소 입찰가 32,000원 이상 입력해 주세요.')).toBeInTheDocument();
  });

  it('기본 입찰가는 SSE 최소 입찰가가 올라가도 바꾸지 않고 제출을 막는다',async()=>{
    renderDialog();
    const input=await screen.findByRole('spinbutton');
    await screen.findByText('100,000P');
    expect(input).toHaveValue(11_000);

    act(()=>mocks.streamHandler?.(bidEvent));

    await waitFor(()=>expect(input).toHaveValue(11_000));
    expect(screen.getByRole('button',{name:'11,000원 입찰하기'})).toBeDisabled();
    expect(screen.getByText('최소 입찰가 32,000원 이상 입력해 주세요.')).toBeInTheDocument();
  });

  it('SSE 최소 입찰가보다 높은 입력값은 유지한다',async()=>{
    renderDialog();
    const user=userEvent.setup();
    const input=await screen.findByRole('spinbutton');
    await screen.findByText('100,000P');

    await user.clear(input);
    await user.type(input,'50000');
    act(()=>mocks.streamHandler?.(bidEvent));

    expect(input).toHaveValue(50_000);
    expect(screen.getByRole('button',{name:'50,000원 입찰하기'})).toBeInTheDocument();
  });

  it('입찰 성공 응답으로 참여 경매 대시보드를 최고 입찰 상태로 갱신한다',async()=>{
    const{queryClient}=renderDialog();
    mocks.createBid.mockResolvedValue({
      bid:{id:10,amount:11_000,status:'LEADING',created_at:'2026-08-04T01:00:00Z'},
      auction:{id:1,current_price:11_000,minimum_bid:12_000,bid_count:2,ends_at:'2099-08-04T10:00:00Z'},
      wallet:{available_balance:89_000,frozen_balance:11_000},
    });
    const dashboardKey=[...dashboardQueryKey,'participating-auctions','ENDING_SOON'];
    queryClient.setQueryData(dashboardKey,[auction]);

    await userEvent.setup().click(await screen.findByRole('button',{name:'11,000원 입찰하기'}));

    await waitFor(()=>expect(mocks.createBid).toHaveBeenCalledOnce());
    await waitFor(()=>expect(queryClient.getQueryData<AuctionDto[]>(dashboardKey)?.[0]).toMatchObject({
      currentPrice:11_000,bidCount:2,myBidStatus:'LEADING',myBidAmount:11_000,
    }));
    expect(await screen.findByText('피카츄 카드를 11,000원에 입찰하였습니다.')).toBeInTheDocument();
  });

  it('즉시 구매는 동의 후에만 가능하고 즉시 구매가로 요청한다',async()=>{
    mocks.createBid.mockResolvedValue({bid:{id:11,amount:20_000,status:'WON',created_at:'2026-08-04T01:00:00Z'},auction:{id:1,current_price:20_000,minimum_bid:20_000,bid_count:2,ends_at:'2099-08-04T10:00:00Z'},wallet:{available_balance:80_000,frozen_balance:0}});
    renderDialog();
    const user=userEvent.setup();
    await user.click(await screen.findByRole('tab',{name:'즉시 구매'}));
    const submit=screen.getByRole('button',{name:'20,000원 즉시 구매하기'});
    expect(submit).toBeDisabled();
    await user.click(screen.getByRole('checkbox',{name:'즉시 구매 시 취소할 수 없음에 동의합니다.'}));
    await user.click(submit);
    await waitFor(()=>expect(mocks.createBid).toHaveBeenCalledWith(1,20_000,expect.any(String)));
    expect(await screen.findAllByText('피카츄 카드를 20,000원에 즉시 구매하였습니다.')).not.toHaveLength(0);
  });

  it('즉시 구매가가 없는 경매는 즉시 구매 탭과 상시 안내를 표시한다',async()=>{
    render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><AuctionBidDialog auction={{...auction,buyNowPrice:null}} onClose={vi.fn()}/></QueryClientProvider>);
    expect(await screen.findByRole('tab',{name:'즉시 구매'})).toBeDisabled();
    expect(await screen.findByText('이 경매는 즉시 구매를 지원하지 않습니다.')).toBeInTheDocument();
  });

  it('일반 입찰가가 즉시 구매가와 같거나 높으면 확인 후 즉시 구매가로 요청한다',async()=>{
    mocks.createBid.mockResolvedValue({bid:{id:12,amount:20_000,status:'WON',created_at:'2026-08-04T01:00:00Z'},auction:{id:1,current_price:20_000,minimum_bid:20_000,bid_count:2,ends_at:'2099-08-04T10:00:00Z'},wallet:{available_balance:80_000,frozen_balance:0}});
    renderDialog();
    const user=userEvent.setup();
    const input=await screen.findByRole('spinbutton');
    await user.clear(input);
    await user.type(input,'20000');
    await user.click(screen.getByRole('button',{name:'20,000원 입찰하기'}));
    expect(screen.getByRole('dialog',{name:'즉시 구매 확인'})).toHaveTextContent('20,000원에 즉시 구매');
    expect(mocks.createBid).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button',{name:'확인'}));
    await waitFor(()=>expect(mocks.createBid).toHaveBeenCalledWith(1,20_000,expect.any(String)));
    expect(await screen.findAllByText('피카츄 카드를 20,000원에 즉시 구매하였습니다.')).not.toHaveLength(0);
  });
});
