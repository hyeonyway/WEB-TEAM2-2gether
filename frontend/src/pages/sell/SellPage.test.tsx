import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import SellPage from './index';

const{registerAuction}=vi.hoisted(()=>({
  registerAuction:vi.fn(),
}));

vi.mock('../../queries/sellMutations',()=>({
  createRegistrationSubmission:()=>({}),
  sellMutations:{
    register:()=>({
      mutationKey:['sell','register'],
      mutationFn:registerAuction,
    }),
  },
}));

function renderSellPage(){
  const queryClient=new QueryClient({
    defaultOptions:{queries:{retry:false},mutations:{retry:false}},
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/sell']}>
        <Routes>
          <Route path="/sell" element={<SellPage Header={undefined}/>}/>
          <Route path="/auction/:auctionId" element={<h1>등록한 경매 상세</h1>}/>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SellPage',()=>{
  beforeEach(()=>{
    registerAuction.mockResolvedValue({id:77});
    vi.stubGlobal('URL',{
      ...URL,
      createObjectURL:vi.fn().mockReturnValue('blob:card-photo'),
      revokeObjectURL:vi.fn(),
    });
  });

  afterEach(()=>{
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it('등록 성공 후 경매 상세로 SPA 이동한다',async()=>{
    const user=userEvent.setup();
    renderSellPage();

    await user.type(screen.getByLabelText(/카드명/),'피카츄');
    await user.click(screen.getByRole('button',{name:/다음 단계/}));

    await user.upload(
      screen.getByLabelText(/사진 추가/),
      new File(['card'], 'card.png', {type:'image/png'}),
    );
    await user.type(screen.getByLabelText(/상품 상태 및 설명/),'상태가 좋은 카드입니다.');
    await user.click(screen.getByRole('button',{name:/다음 단계/}));

    await user.type(screen.getByLabelText(/시작가/),'10000');
    await user.click(screen.getByRole('button',{name:/다음 단계/}));
    await user.click(screen.getByRole('button',{name:'경매 등록'}));

    expect(await screen.findByText(/경매 등록이 완료되었습니다/)).toBeInTheDocument();
    expect(await screen.findByRole('heading',{name:'등록한 경매 상세'},{timeout:1000}))
      .toBeInTheDocument();
  });
});
