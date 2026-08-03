import {describe,expect,it} from 'vitest';
import {auctionQueryKeys} from './auctionQueries';

describe('auctionQueryKeys',()=>{
  const query={keyword:'',psaGrade:null,page:0,size:12,sort:'BID_COUNT' as const};

  it('공개 조회와 로그인 사용자 조회의 목록 캐시를 분리한다',()=>{
    expect(auctionQueryKeys.list(query,'public'))
      .not.toEqual(auctionQueryKeys.list(query,'self'));
  });

  it('공개 조회와 로그인 사용자 조회의 상세 캐시를 분리한다',()=>{
    expect(auctionQueryKeys.detail(1,'public'))
      .not.toEqual(auctionQueryKeys.detail(1,'self'));
  });
});
