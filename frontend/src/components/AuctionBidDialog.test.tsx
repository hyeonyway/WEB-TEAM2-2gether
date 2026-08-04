import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import type {AuctionDto,BidContextResponseDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import AuctionBidDialog from './AuctionBidDialog';

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
  startPrice:9_000,currentPrice:10_000,bidIncrement:1_000,bidCount:1,
  endsAt:'2099-08-04T10:00:00Z',status:'OPEN',version:1,myBidStatus:'NONE',myBidAmount:null,
};

const context:BidContextResponseDto={
  auction_id:1,status:'OPEN',version:1,current_price:10_000,minimum_bid:11_000,bid_increment:1_000,
  my_bid_status:'NONE',my_bid_amount:null,wallet:{available_balance:100_000,frozen_balance:0},recent_bids:[],
};

const bidEvent:AuctionStreamPayload={
  type:'BID_PLACED',auction_id:1,bidder_id:2,previous_bidder_id:null,
  start_price:9_000,current_price:30_000,bid_increment:2_000,bid_count:2,
  ends_at:'2099-08-04T11:00:00Z',status:'OPEN',auction_version:2,occurred_at:'2026-08-04T01:00:00Z',
};

function renderDialog(){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return render(<QueryClientProvider client={queryClient}>
    <AuctionBidDialog auction={auction} onClose={vi.fn()}/>
  </QueryClientProvider>);
}

describe('AuctionBidDialog',()=>{
  beforeEach(()=>{
    mocks.fetchContext.mockReset().mockResolvedValue(context);
    mocks.createBid.mockReset();
    mocks.streamHandler=null;
    vi.stubGlobal('matchMedia',vi.fn().mockReturnValue({matches:true}));
  });

  it('SSE 최소 입찰가보다 낮은 입력값과 제출 버튼을 새 최소 입찰가로 올린다',async()=>{
    renderDialog();
    const user=userEvent.setup();
    const input=await screen.findByRole('spinbutton');
    await screen.findByText('100,000P');
    expect(input).toHaveValue(11_000);

    await user.clear(input);
    await user.type(input,'25000');
    expect(screen.getByRole('button',{name:'25,000원 입찰하기'})).toBeInTheDocument();

    act(()=>mocks.streamHandler?.(bidEvent));

    await waitFor(()=>expect(input).toHaveValue(32_000));
    expect(screen.getByRole('button',{name:'32,000원 입찰하기'})).toBeInTheDocument();
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
});
