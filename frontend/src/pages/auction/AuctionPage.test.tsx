import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import {AuthContext} from '../../auth/AuthProvider';
import type {AuctionDto} from '../../dto/auctionDto';
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
  endsAt:'2099-08-04T10:00:00Z',status:'OPEN',version:1,myBidStatus:'NONE',myBidAmount:null,
});

let intersectionCallback:IntersectionObserverCallback;
let onAuctionUpdated:(event:unknown)=>void;

class TestIntersectionObserver implements IntersectionObserver{
  readonly root=null;
  readonly rootMargin='0px';
  readonly scrollMargin='0px';
  readonly thresholds=[0];
  constructor(callback:IntersectionObserverCallback){intersectionCallback=callback;}
  disconnect=vi.fn();
  observe=vi.fn();
  takeRecords=()=>[];
  unobserve=vi.fn();
}

function renderPage(){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const result=render(<QueryClientProvider client={queryClient}>
    <MemoryRouter initialEntries={['/auction']}>
      <AuthContext.Provider value={{status:'anonymous',retryInitialization:vi.fn()}}>
        <AuctionPage/>
      </AuthContext.Provider>
    </MemoryRouter>
  </QueryClientProvider>);
  return {...result,queryClient};
}

describe('AuctionPage',()=>{
  beforeEach(()=>{
    apiMocks.fetchAuctions.mockReset()
      .mockResolvedValueOnce({content:[auction(2,'피카츄')],next_cursor:'next-token',has_next:true})
      .mockResolvedValueOnce({content:[auction(1,'리자몽')],next_cursor:null,has_next:false});
    streamMocks.useAuctionStream.mockImplementation(({onAuctionUpdated:callback})=>{
      onAuctionUpdated=callback;
    });
    vi.stubGlobal('IntersectionObserver',TestIntersectionObserver);
  });

  it('목록 하단이 보이면 다음 cursor를 조회해 경매를 누적한다',async()=>{
    renderPage();

    expect(await screen.findByText('피카츄')).toBeInTheDocument();
    await act(async()=>intersectionCallback([
      {isIntersecting:true} as IntersectionObserverEntry,
    ],{} as IntersectionObserver));

    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenCalledTimes(2));
    expect(apiMocks.fetchAuctions).toHaveBeenLastCalledWith(
      expect.objectContaining({sort:'BID_COUNT',size:12}),
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

  it('SSE 이벤트를 받으면 페이지 경계를 유지하도록 목록을 다시 조회한다',async()=>{
    renderPage();
    expect(await screen.findByText('피카츄')).toBeInTheDocument();

    await act(async()=>onAuctionUpdated({type:'BID_PLACED'}));

    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenCalledTimes(2));
    expect(apiMocks.fetchAuctions).toHaveBeenLastCalledWith(
      expect.objectContaining({sort:'BID_COUNT'}),
      undefined,
    );
  });

  it('다음 페이지 조회가 실패해도 기존 목록과 재시작 버튼을 유지한다',async()=>{
    apiMocks.fetchAuctions.mockReset()
      .mockResolvedValueOnce({content:[auction(2,'피카츄')],next_cursor:'next-token',has_next:true})
      .mockRejectedValueOnce(new Error('next page failed'))
      .mockResolvedValueOnce({content:[auction(2,'피카츄')],next_cursor:null,has_next:false});
    const user=userEvent.setup();
    renderPage();

    expect(await screen.findByText('피카츄')).toBeInTheDocument();
    await act(async()=>intersectionCallback([
      {isIntersecting:true} as IntersectionObserverEntry,
    ],{} as IntersectionObserver));

    const retry=await screen.findByRole('button',{name:'목록 새로고침'});
    expect(screen.getByText('피카츄')).toBeInTheDocument();
    await user.click(retry);
    await waitFor(()=>expect(apiMocks.fetchAuctions).toHaveBeenLastCalledWith(
      expect.objectContaining({sort:'BID_COUNT'}),
      undefined,
    ));
  });
});
