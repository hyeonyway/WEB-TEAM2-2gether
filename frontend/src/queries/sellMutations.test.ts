import {QueryClient} from '@tanstack/react-query';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import type {CardDto} from '../dto/auctionDto';
import type {RegisterAuctionRequestDto} from '../dto/sellDto';
import {auctionQueryKeys} from './auctionQueries';
import {sellMutations} from './sellMutations';

const mocks=vi.hoisted(()=>({
  fetchCardPage:vi.fn(),
  fetchCardDetail:vi.fn(),
  uploadSellImages:vi.fn(),
  createAuction:vi.fn(),
}));

vi.mock('../api/auctionApi',()=>({
  fetchCardPage:mocks.fetchCardPage,
  fetchCardDetail:mocks.fetchCardDetail,
}));

vi.mock('../api/sellApi',()=>({
  uploadSellImages:mocks.uploadSellImages,
  createAuction:mocks.createAuction,
}));

const card=(id:number):CardDto=>({
  id,
  name:'피카츄',
  marketPrice:10000,
  lowPrice:9000,
  highPrice:11000,
  changeRate:0,
  theme:'gold',
  bidCount:0,
  psaGrade:'10',
  language:'JP',
  imageUrl:null,
});

const request: RegisterAuctionRequestDto={
  form:{
    cardName:'피카츄',setName:'정확한 세트',year:'2026',cardNumber:'1',language:'일본어',
    gradeType:'psa',psaGrade:'10',population:'',selfGrade:'',description:'설명',sellerMemo:'',
    startPrice:'10000',bidIncrement:'1000',buyNowEnabled:true,buyNowPrice:'20000',
    duration:'12',shipping:'3000',
  },
  photos:[{id:'photo-1',file:new File(['image'],'front.jpg',{type:'image/jpeg'}),url:'blob:front'}],
  psaCertification:'12345678',
};

describe('sellMutations',()=>{
  beforeEach(()=>{
    vi.clearAllMocks();
    mocks.fetchCardPage.mockResolvedValue({
      content:[card(1),card(2)],page:0,size:100,total_elements:2,has_next:false,
    });
    mocks.fetchCardDetail.mockImplementation((id:number)=>Promise.resolve({
      id,
      name:'피카츄',
      set_name:id===1?'다른 세트':'정확한 세트',
      psa_grade:'PSA 10',
      language:'Japanese',
    }));
    mocks.uploadSellImages.mockResolvedValue([{order:0,uploadToken:'upload/front.jpg'}]);
    mocks.createAuction.mockResolvedValue({id:20});
  });

  it('중복 카드명은 세트와 언어와 PSA 등급까지 일치하는 카드로 식별한다',async()=>{
    const mutationFn=sellMutations.register().mutationFn;

    await mutationFn(request,{} as never);

    expect(mocks.createAuction).toHaveBeenCalledWith(
      expect.objectContaining({itemId:2}),
      expect.any(String),
    );
  });

  it('동일한 등록을 재시도하면 업로드 결과와 멱등성 키를 재사용한다',async()=>{
    mocks.createAuction
      .mockRejectedValueOnce(new Error('응답 유실'))
      .mockResolvedValueOnce({id:20});
    const mutationFn=sellMutations.register().mutationFn;

    await expect(mutationFn(request,{} as never)).rejects.toThrow('응답 유실');
    await mutationFn(request,{} as never);

    expect(mocks.uploadSellImages).toHaveBeenCalledTimes(1);
    expect(mocks.createAuction).toHaveBeenCalledTimes(2);
    expect(mocks.createAuction.mock.calls[0][0]).toBe(mocks.createAuction.mock.calls[1][0]);
    expect(mocks.createAuction.mock.calls[0][1]).toBe(mocks.createAuction.mock.calls[1][1]);
  });

  it('식별 필드가 같은 카드가 여러 건이면 첫 카드를 임의 선택하지 않는다',async()=>{
    mocks.fetchCardDetail.mockImplementation((id:number)=>Promise.resolve({
      id,name:'피카츄',set_name:'정확한 세트',psa_grade:'10',language:'JP',
    }));
    const mutationFn=sellMutations.register().mutationFn;

    await expect(mutationFn(request,{} as never))
      .rejects.toThrow('카드 정보가 여러 건입니다.');

    expect(mocks.uploadSellImages).not.toHaveBeenCalled();
    expect(mocks.createAuction).not.toHaveBeenCalled();
  });

  it('등록 성공 시 기존 경매 목록 Query를 무효화한다',async()=>{
    const queryClient=new QueryClient();
    const auctionListKey=auctionQueryKeys.list({
      keyword:'',psaGrade:null,size:12,sort:'LATEST',
    },'public');
    const unrelatedKey=['cards','list'] as const;
    queryClient.setQueryData(auctionListKey,{content:[]});
    queryClient.setQueryData(unrelatedKey,{content:[]});
    const mutation=sellMutations.register();

    await mutation.onSuccess?.(
      {id:20},
      request,
      undefined,
      {client:queryClient,meta:undefined,mutationKey:['sell','register']},
    );

    expect(queryClient.getQueryState(auctionListKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(unrelatedKey)?.isInvalidated).toBe(false);
  });
});
