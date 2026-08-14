import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,useLocation} from 'react-router-dom';
import {describe,expect,it,vi} from 'vitest';
import type {CardDto} from '../../../dto/auctionDto';
import CardCatalog from './CardCatalog';

vi.mock('./CardFavoriteButton',()=>({default:()=>null}));

const card:CardDto={
  id:1,name:'피카츄',marketPrice:10000,lowPrice:9000,highPrice:11000,changeRate:0,
  theme:'gold',bidCount:0,psaGrade:'PSA 10',language:'JP',imageUrl:'/card.webp',
};

function LocationProbe(){
  const location=useLocation();
  return <output data-testid="catalog-path">{location.pathname}</output>;
}

function renderCatalog(queryClient=new QueryClient()){
  return render(<QueryClientProvider client={queryClient}>
    <MemoryRouter>
      <CardCatalog cards={[card]}/>
      <LocationProbe/>
    </MemoryRouter>
  </QueryClientProvider>);
}

describe('CardCatalog',()=>{
  it('PSA 접두사가 포함된 등급도 접두사를 한 번만 표시한다',()=>{
    const{container}=renderCatalog();

    expect(screen.getAllByText(/PSA 10/)).toHaveLength(1);
    expect(screen.queryByText(/PSA PSA 10/)).not.toBeInTheDocument();
    expect(container.querySelector('.catalog-image-viewport .catalog-card-image'))
      .toBeInTheDocument();
  });

  it('카드 상세를 SPA로 이동하며 Query cache를 유지한다',async()=>{
    const queryClient=new QueryClient();
    queryClient.setQueryData(['navigation-state'],{preserved:true});
    const user=userEvent.setup();
    renderCatalog(queryClient);

    await user.click(screen.getByRole('link',{name:/피카츄/}));

    expect(screen.getByTestId('catalog-path')).toHaveTextContent('/cards/1');
    expect(queryClient.getQueryData(['navigation-state'])).toEqual({preserved:true});
  });
});
