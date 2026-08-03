import {render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe,expect,it,vi} from 'vitest';
import {AuthContext} from '../../../auth/AuthProvider';
import ToastContainer from '../../../components/Toast';
import type {AuctionDto} from '../../../dto/auctionDto';
import AuctionCatalog from './AuctionCatalog';

const auction:AuctionDto={
  id:1,
  card:{
    id:1,name:'피카츄',marketPrice:10000,lowPrice:9000,highPrice:11000,changeRate:0,
    theme:'gold',bidCount:0,psaGrade:'10',language:'JP',imageUrl:null,
  },
  startPrice:10000,currentPrice:12000,bidIncrement:1000,bidCount:1,
  endsAt:'2099-08-03T12:00:00',status:'OPEN',myBidStatus:'NONE',myBidAmount:null,version:1,
};

describe('AuctionCatalog',()=>{
  it('PSA 접두사가 포함된 등급도 한 번만 표시한다',()=>{
    render(<AuthContext.Provider value={{status:'anonymous',retryInitialization:vi.fn()}}>
      <AuctionCatalog auctions={[{...auction,card:{...auction.card,psaGrade:'PSA 10'}}]}/>
    </AuthContext.Provider>);

    expect(screen.getByText('PSA 10')).toBeInTheDocument();
    expect(screen.queryByText('PSA PSA 10')).not.toBeInTheDocument();
  });

  it('비로그인 사용자가 입찰을 누르면 다이얼로그 대신 로그인 안내를 표시한다',async()=>{
    const user=userEvent.setup();
    render(<AuthContext.Provider value={{status:'anonymous',retryInitialization:vi.fn()}}>
      <AuctionCatalog auctions={[auction]}/>
      <ToastContainer/>
    </AuthContext.Provider>);

    await user.click(screen.getByRole('button',{name:'입찰하기'}));

    expect(screen.getByText('로그인이 필요합니다')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
