import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import SellPage from './index';

const mocks=vi.hoisted(()=>({
  fetchCardPage:vi.fn(),
  fetchCardDetail:vi.fn(),
  lookupPsaCertification:vi.fn(),
  fetchPsaCertificationSample:vi.fn(),
}));

vi.mock('../../api/auctionApi',()=>({
  fetchCardPage:mocks.fetchCardPage,
  fetchCardDetail:mocks.fetchCardDetail,
}));

vi.mock('../../api/sellApi',()=>({
  lookupPsaCertification:mocks.lookupPsaCertification,
  fetchPsaCertificationSample:mocks.fetchPsaCertificationSample,
  scanCardImage:vi.fn(),
}));

vi.mock('../../queries/sellMutations',()=>({
  createRegistrationSubmission:vi.fn(()=>({})),
  sellMutations:{register:vi.fn(()=>({mutationFn:vi.fn()}))},
}));

function renderPage(){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return render(<QueryClientProvider client={queryClient}><MemoryRouter><SellPage Header={()=>null}/></MemoryRouter></QueryClientProvider>);
}

describe('SellPage card selection',()=>{
  beforeEach(()=>{
    vi.clearAllMocks();
    mocks.fetchCardPage.mockResolvedValue({
      content:[
        ['10','Pokemon TCG'],
        ['근민트','Pokemon TCG'],
        ['민트','Pokemon TCG'],
        ['근민트','Pokemon Promo'],
      ].map(([psaGrade,setName],index)=>({
        id:100+index,
        name:'피카츄 프로모',
        marketPrice:0,
        lowPrice:0,
        highPrice:0,
        changeRate:0,
        theme:'gold',
        bidCount:0,
        psaGrade,
        language:'JP',
        imageUrl:null,
        setName,
      })),
      page:0,
      size:100,
      total_elements:1,
      has_next:false,
    });
    mocks.lookupPsaCertification.mockResolvedValue({itemId:104,gradeType:'psa',psaGrade:'7',population:'1234'});
    mocks.fetchPsaCertificationSample.mockResolvedValue({certificationNumber:'12345678'});
  });

  it('등급 변형은 하나의 카드 검색 결과로 묶어 선택한다',async()=>{
    mocks.fetchCardDetail.mockReturnValue(new Promise(()=>{}));
    const user=userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('카드명 필수'),'피카');
    const results=await screen.findAllByRole('button',{name:/피카츄 프로모/});

    expect(results).toHaveLength(2);
    expect(results[0]).toHaveTextContent('Pokemon TCG');

    await user.click(results[0]);

    await waitFor(()=>expect(screen.getByText(/선택됨: 피카츄 프로모 .* 근민트/)).toBeInTheDocument());
  });

  it('검색 결과를 선택하지 않은 경우 선택 방법을 안내한다',async()=>{
    const user=userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('카드명 필수'),'피카');

    expect(await screen.findByText('검색 결과에서 카드를 선택해 주세요.')).toBeInTheDocument();
  });

  it('등록된 PSA 인증번호는 카드 검색 없이 fixture 카드로 자동 선택한다',async()=>{
    mocks.fetchCardDetail.mockResolvedValue({
      id:104,name:'피카츄 프로모',set_name:'Pokemon TCG',psa_grade:'PSA 7',language:'JP',
    });
    const user=userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button',{name:'PSA 인증 조회'}));
    await user.type(screen.getByLabelText('PSA 인증번호'),'12345678');
    await user.click(screen.getByRole('button',{name:'조회'}));

    await waitFor(()=>expect(mocks.fetchCardDetail).toHaveBeenCalledWith(104));
    expect(await screen.findByText('선택됨: 피카츄 프로모 · Pokemon TCG · PSA 7')).toBeInTheDocument();
  });

  it('미등록 PSA 인증번호는 기존 카드 선택을 유지하고 인라인 오류를 표시한다',async()=>{
    mocks.fetchCardDetail.mockResolvedValue({
      id:100,name:'피카츄 프로모',set_name:'Pokemon TCG',psa_grade:'근민트',language:'JP',
    });
    mocks.lookupPsaCertification.mockRejectedValue(new Error('not found'));
    const user=userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('카드명 필수'),'피카');
    await user.click((await screen.findAllByRole('button',{name:/피카츄 프로모/}))[0]);
    await screen.findByText('선택됨: 피카츄 프로모 · Pokemon TCG · 근민트');
    await user.click(screen.getByRole('button',{name:'PSA 인증 조회'}));
    await user.type(screen.getByLabelText('PSA 인증번호'),'12345678');
    await user.click(screen.getByRole('button',{name:'조회'}));

    expect(await screen.findByText('등록된 PSA 번호가 아닙니다.')).toBeInTheDocument();
    expect(screen.getByText('선택됨: 피카츄 프로모 · Pokemon TCG · 근민트')).toBeInTheDocument();
  });

  it('예시 인증번호 채우기는 조회 입력값만 채운다',async()=>{
    const user=userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button',{name:'PSA 인증 조회'}));
    await user.click(screen.getByRole('button',{name:'예시 인증번호 채우기'}));

    expect(mocks.fetchPsaCertificationSample).toHaveBeenCalledOnce();
    expect(screen.getByLabelText('PSA 인증번호')).toHaveValue('12345678');
    expect(mocks.lookupPsaCertification).not.toHaveBeenCalled();
  });

  it('카드 선택 뒤에도 자체 평가 등급을 한 번의 클릭으로 변경한다',async()=>{
    mocks.fetchCardDetail.mockImplementation((id:number)=>Promise.resolve({
      id,name:'피카츄 프로모',set_name:'Pokemon TCG',psa_grade:id===102?'민트':id===100?'10':'근민트',language:'JP',
    }));
    const user=userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('카드명 필수'),'피카');
    const [result]=await screen.findAllByRole('button',{name:/피카츄 프로모/});
    await user.click(result);
    await screen.findByText(/선택됨: 피카츄 프로모/);

    await user.click(screen.getByRole('button',{name:'민트'}));

    expect(screen.getByRole('button',{name:'민트'})).toHaveClass('active');
    await waitFor(()=>expect(screen.getByText('선택됨: 피카츄 프로모 · Pokemon TCG · 민트')).toBeInTheDocument());
  });

  it('PSA 상태를 선택하면 같은 카드의 PSA 항목으로 바꾼다',async()=>{
    mocks.fetchCardDetail.mockImplementation((id:number)=>Promise.resolve({
      id,name:'피카츄 프로모',set_name:'Pokemon TCG',psa_grade:id===100?'10':'근민트',language:'JP',
    }));
    const user=userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('카드명 필수'),'피카');
    const [result]=await screen.findAllByRole('button',{name:/피카츄 프로모/});
    await user.click(result);
    await screen.findByText(/선택됨: 피카츄 프로모/);

    await user.click(screen.getByRole('button',{name:'PSA 등급'}));

    await waitFor(()=>expect(screen.getByText('선택됨: 피카츄 프로모 · Pokemon TCG · 10')).toBeInTheDocument());
    expect(screen.getByRole('button',{name:'PSA 등급'})).toHaveClass('active');
  });

  it('즉시 구매가를 켠 뒤 금액이 없으면 원인을 표시한다',async()=>{
    mocks.fetchCardDetail.mockResolvedValue({
      id:101,name:'피카츄 프로모',set_name:'Pokemon TCG',psa_grade:'근민트',language:'JP',
    });
    const user=userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('카드명 필수'),'피카');
    const [result]=await screen.findAllByRole('button',{name:/피카츄 프로모/});
    await user.click(result);
    await screen.findByText(/선택됨: 피카츄 프로모/);
    await user.click(screen.getByRole('button',{name:/다음 단계/}));

    const file=new File(['image'],'front.jpg',{type:'image/jpeg'});
    await user.upload(document.querySelector('#product-photos')!,file);
    await user.type(screen.getByLabelText('상품 상태 및 설명 필수'),'설명');
    await user.click(screen.getByRole('button',{name:/다음 단계/}));
    await user.click(document.querySelector('#buy-toggle')!);

    expect(screen.getByText('즉시 구매가를 입력해 주세요.')).toBeInTheDocument();
  });

  it('등록 사진의 Blob URL을 화면 이탈 시 해제한다',async()=>{
    const createObjectURL=vi.fn(()=> 'blob:photo-1');
    const revokeObjectURL=vi.fn();
    const originalCreate=URL.createObjectURL;
    const originalRevoke=URL.revokeObjectURL;
    Object.defineProperty(URL,'createObjectURL',{configurable:true,value:createObjectURL});
    Object.defineProperty(URL,'revokeObjectURL',{configurable:true,value:revokeObjectURL});
    mocks.fetchCardDetail.mockResolvedValue({
      id:101,name:'피카츄 프로모',set_name:'Pokemon TCG',psa_grade:'근민트',language:'JP',
    });
    const user=userEvent.setup();
    const page=renderPage();

    await user.type(screen.getByLabelText('카드명 필수'),'피카');
    const [result]=await screen.findAllByRole('button',{name:/피카츄 프로모/});
    await user.click(result);
    await screen.findByText(/선택됨: 피카츄 프로모/);
    await user.click(screen.getByRole('button',{name:/다음 단계/}));
    await user.upload(document.querySelector('#product-photos')!,new File(['image'],'front.jpg',{type:'image/jpeg'}));

    page.unmount();

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:photo-1');
    Object.defineProperty(URL,'createObjectURL',{configurable:true,value:originalCreate});
    Object.defineProperty(URL,'revokeObjectURL',{configurable:true,value:originalRevoke});
  });
});
