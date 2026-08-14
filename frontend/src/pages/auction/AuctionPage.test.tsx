import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,render,screen,waitFor,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,useLocation} from 'react-router-dom';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import {AuthContext} from '../../auth/AuthProvider';
import type {AuctionDto} from '../../dto/auctionDto';
import type {AuctionStreamPayload} from '../../hooks/useAuctionStream';
import {auctionQueryKeys} from '../../queries/auctionQueries';
import AuctionPage from './AuctionPage';

const apiMocks=vi.hoisted(()=>({fetchAuctions:vi.fn()}));
const streamMocks=vi.hoisted(()=>({useAuctionStream:vi.fn()}));

vi.mock('../../api/auctionApi',async importOriginal=>({
  ...await importOriginal<typeof import('../../api/auctionApi')>(),
  fetchAuctions:apiMocks.fetchAuctions,
}));
vi.mock('../../hooks/useAuctionStream',()=>({useAuctionStream:streamMocks.useAuctionStream}));

const auction=(id:number,name:string):AuctionDto=>({
  id,
  card:{id,name,marketPrice:10_000,lowPrice:10_000,highPrice:10_000,changeRate:0,theme:'gold',bidCount:1,psaGrade:'10',language:'KR',imageUrl:null},
  startPrice:10_000,currentPrice:10_000,bidIncrement:1_000,bidCount:1,
  endsAt:'2099-08-04T10:00:00Z',status:'OPEN',myBidStatus:'NONE',myBidAmount:null,
});

let intersectionCallback:IntersectionObserverCallback;
let onAuctionUpdated:(event:AuctionStreamPayload)=>void;
let observeCount=0;

class TestIntersectionObserver implements IntersectionObserver{
  readonly root=null;
  readonly rootMargin='0px';
  readonly scrollMargin='0px';
  readonly thresholds=[0];
  constructor(callback:IntersectionObserverCallback){intersectionCallback=callback;}
  disconnect=vi.fn();
  observe=vi.fn(()=>{observeCount++;});
  takeRecords=()=>[];
  unobserve=vi.fn();
}

function LocationProbe(){
  return <output data-testid="location-search">{useLocation().search}</output>;
}

function renderPage(initialEntry='/auction',authStatus:'initializing'|'authenticated'|'anonymous'='anonymous'){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const result=render(<QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <AuthContext.Provider value={{status:authStatus,retryInitialization:vi.fn()}}>
          <AuctionPage/><LocationProbe/>
      </AuthContext.Provider>
    </MemoryRouter>
  </QueryClientProvider>);
  return {...result,queryClient};
}

describe('AuctionPage',()=>{
  beforeEach(()=>{
    observeCount=0;
    apiMocks.fetchAuctions.mockReset()
      .mockResolvedValueOnce({content:[auction(2,'피카츄')],next_cursor:'next-token',has_next:true})
      .mockResolvedValueOnce({content:[auction(1,'리자몽')],next_cursor:null,has_next:false});
    streamMocks.useAuctionStream.mockImplementation(({onAuctionUpdated:callback})=>{
      onAuctionUpdated=callback;
    });
    vi.stubGlobal('IntersectionObserver',TestIntersectionObserver);
    vi.stubGlobal('matchMedia',vi.fn(()=>({matches:true})));
  });

  it('인증 초기화 중에는 사용자 범위가 확정될 때까지 목록을 조회하지 않는다',async()=>{
    renderPage('/auction','initializing');

    expect(screen.getByLabelText('경매 목록을 불러오는 중')).toBeInTheDocument();
    expect(apiMocks.fetchAuctions).not.toHaveBeenCalled();
  });

  it('처음 진입하면 최신순으로 조회하고 정렬 버튼을 최신순부터 배치한다',async()=>{
    renderPage();

    await screen.findByText('피카츄');
    expect(apiMocks.fetchAuctions).toHaveBeenCalledWith(expect.objectContaining({sort:'LATEST'}),undefined);
    expect(screen.getAllByRole('button').filter(button=>[
      '최신순','마감 임박순','입찰 수 높은순','경매가 높은순','경매가 낮은순','상승률 높은순',
    ].includes(button.textContent??'')).map(button=>button.textContent)).toEqual([
      '최신순','마감 임박순','입찰 수 높은순','경매가 높은순','경매가 낮은순','상승률 높은순',
    ]);
  });

  it('목록 하단이 보이면 다음 cursor를 조회해 경매를 누적한다',async()=>{
    renderPage();

    expect(await screen.findByText('피카츄')).toBeInTheDocument();
    await act(async()=>intersectionCallback([
      {isIntersecting:true} as IntersectionObserverEntry,
    ],{} as IntersectionObserver));

    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenCalledTimes(2));
    expect(apiMocks.fetchAuctions).toHaveBeenLastCalledWith(
      expect.objectContaining({sort:'LATEST',size:12}),
      'next-token',
    );
    expect(await screen.findByText('리자몽')).toBeInTheDocument();
  });

  it('최신순 정렬을 선택할 수 있다',async()=>{
    renderPage();
    const user=userEvent.setup();

    await user.click(await screen.findByRole('button',{name:'최신순'}));

    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenCalledWith(
      expect.objectContaining({sort:'LATEST'}),
      undefined,
    ));
  });

  it('마감 임박순 정렬을 선택할 수 있다',async()=>{
    renderPage();
    await userEvent.setup().click(await screen.findByRole('button',{name:'마감 임박순'}));
    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenCalledWith(expect.objectContaining({sort:'ENDING_SOON'}),undefined));
  });

  it('선택한 정렬 기준을 URL에 저장해 새로고침 후에도 복원한다',async()=>{
    const firstRender=renderPage();
    const user=userEvent.setup();

    await user.click(await screen.findByRole('button',{name:'경매가 높은순'}));

    expect(screen.getByTestId('location-search')).toHaveTextContent('sort=PRICE_HIGH');
    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenLastCalledWith(
      expect.objectContaining({sort:'PRICE_HIGH'}),
      undefined,
    ));

    firstRender.unmount();
    apiMocks.fetchAuctions.mockResolvedValue({
      content:[auction(1,'경매가 높은 경매')],next_cursor:null,has_next:false,
    });
    const secondRender=renderPage('/auction?sort=PRICE_HIGH');
    expect(await screen.findByRole('button',{name:'경매가 높은순'})).toHaveClass('active');
    secondRender.unmount();
  });

  it('SSE 입찰 이벤트는 목록을 다시 조회하지 않고 캐시된 경매를 갱신한다',async()=>{
    const{queryClient}=renderPage();
    expect(await screen.findByText('피카츄')).toBeInTheDocument();

    await act(async()=>onAuctionUpdated({
      type:'BID_PLACED',auction_id:2,bidder_id:7,previous_bidder_id:null,
      start_price:10_000,current_price:15_000,bid_increment:1_000,bid_count:2,
      ends_at:'2099-08-04T10:00:00Z',status:'OPEN',event_id:2,
      occurred_at:'2026-08-04T10:00:00Z',
    }));

    expect(apiMocks.fetchAuctions).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData<{pages:{content:AuctionDto[]}[]}>(
      auctionQueryKeys.list({keyword:'',psaGrade:null,sort:'LATEST',size:12},'public'),
    )?.pages[0].content[0].currentPrice).toBe(15_000);
  });

  it('SSE 입찰 이벤트로 정렬 기준 값이 바뀌면 표시 순서도 다시 정렬한다',async()=>{
    const first=auction(2,'입찰 수가 적은 경매');
    const second=auction(1,'입찰 수가 많은 경매');
    apiMocks.fetchAuctions.mockReset().mockResolvedValueOnce({
      content:[first,second],next_cursor:null,has_next:false,
    });
    renderPage('/auction?sort=BID_COUNT');
    expect(await screen.findByText('입찰 수가 적은 경매')).toBeInTheDocument();

    await act(async()=>onAuctionUpdated({
      type:'BID_PLACED',auction_id:1,bidder_id:7,previous_bidder_id:null,
      start_price:10_000,current_price:15_000,bid_increment:1_000,bid_count:10,
      ends_at:'2099-08-04T10:00:00Z',status:'OPEN',event_id:2,
      occurred_at:'2026-08-04T10:00:00Z',
    }));

    await waitFor(()=>{
      const cards=screen.getAllByRole('article');
      expect(within(cards[0]).getByRole('heading')).toHaveTextContent('입찰 수가 많은 경매');
    });
  });

  it('다음 페이지 조회가 실패해도 기존 목록과 재시작 버튼을 유지한다',async()=>{
    let rejectNextPage:(reason?:unknown)=>void=()=>{};
    const nextPage=new Promise((_,reject)=>{rejectNextPage=reject;});
    apiMocks.fetchAuctions.mockReset()
      .mockResolvedValueOnce({content:[auction(2,'피카츄')],next_cursor:'next-token',has_next:true})
      .mockReturnValueOnce(nextPage)
      .mockResolvedValueOnce({content:[auction(2,'피카츄')],next_cursor:null,has_next:false});
    const user=userEvent.setup();
    renderPage();

    expect(await screen.findByText('피카츄')).toBeInTheDocument();
    await act(async()=>intersectionCallback([
      {isIntersecting:true} as IntersectionObserverEntry,
    ],{} as IntersectionObserver));
    await waitFor(()=>expect(observeCount).toBeGreaterThan(1));
    const observeCountBeforeFailure=observeCount;
    await act(async()=>rejectNextPage(new Error('next page failed')));

    const retry=await screen.findByRole('button',{name:'목록 새로고침'});
    expect(screen.getByText('피카츄')).toBeInTheDocument();
    expect(observeCount).toBe(observeCountBeforeFailure);
    await user.click(retry);
    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenLastCalledWith(
      expect.objectContaining({sort:'LATEST'}),
      undefined,
    ));
  });
});
