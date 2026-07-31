import {beforeEach,describe,expect,it,vi} from 'vitest';
import {clearAccessToken,setAccessToken} from './accessTokenStore';
import {createAuction,uploadSellImages} from './sellApi';
import type {AuctionPayload,SellPhoto} from '../dto/sellDto';

function jsonResponse(body:unknown,status=200){
  return new Response(JSON.stringify(body),{
    status,
    headers:{'Content-Type':'application/json'},
  });
}

describe('sellApi',()=>{
  beforeEach(()=>{
    vi.restoreAllMocks();
    clearAccessToken();
    setAccessToken('seller-access-token');
  });

  it('presigned URL에 이미지를 업로드하고 업로드 토큰을 반환한다',async()=>{
    const file=new File(['image'],'front.jpg',{type:'image/jpeg'});
    const photo:SellPhoto={id:'photo-1',file,url:'blob:front'};
    const fetchMock=vi.spyOn(globalThis,'fetch')
      .mockResolvedValueOnce(jsonResponse({
        uploads:[{
          upload_url:'https://storage.example/front',
          upload_token:'upload/2026/07/31/front.jpg',
          expires_in_seconds:300,
        }],
      }))
      .mockResolvedValueOnce(new Response(null,{status:200}));

    await expect(uploadSellImages([photo])).resolves.toEqual([
      {order:0,uploadToken:'upload/2026/07/31/front.jpg'},
    ]);
    expect(fetchMock.mock.calls[1]).toEqual([
      'https://storage.example/front',
      expect.objectContaining({
        method:'PUT',
        body:file,
        headers:{'Content-Type':'image/jpeg'},
      }),
    ]);
  });

  it('기존 카드 ID와 업로드 토큰으로 JWT 경매 생성 요청을 보낸다',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(jsonResponse({id:20},201));
    const payload:AuctionPayload={
      itemId:1,
      form:{
        cardName:'피카츄',setName:'세트',year:'2026',cardNumber:'1',language:'일본어',
        gradeType:'psa',psaGrade:'10',population:'',selfGrade:'',description:'설명',
        sellerMemo:'',startPrice:'10000',bidIncrement:'1000',buyNowEnabled:true,
        buyNowPrice:'20000',duration:'12',shipping:'3000',
      },
      photos:[{order:0,uploadToken:'upload/2026/07/31/front.jpg'}],
      psaCertification:null,
    };

    await createAuction(payload);

    const [,options]=fetchMock.mock.calls[0];
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer seller-access-token');
    expect(JSON.parse(String(options?.body))).toEqual({
      itemId:1,
      auctionName:'피카츄',
      description:'설명',
      imageUploadTokens:['upload/2026/07/31/front.jpg'],
      startPrice:10000,
      bidIncrement:1000,
      buyNowPrice:20000,
      durationHours:12,
      shippingFee:3000,
    });
  });
});
