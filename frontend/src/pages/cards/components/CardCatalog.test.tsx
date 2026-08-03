import {render,screen} from '@testing-library/react';
import {describe,expect,it,vi} from 'vitest';
import type {CardDto} from '../../../dto/auctionDto';
import CardCatalog from './CardCatalog';

vi.mock('./CardFavoriteButton',()=>({default:()=>null}));

const card:CardDto={
  id:1,name:'피카츄',marketPrice:10000,lowPrice:9000,highPrice:11000,changeRate:0,
  theme:'gold',bidCount:0,psaGrade:'PSA 10',language:'JP',imageUrl:'/card.webp',
};

describe('CardCatalog',()=>{
  it('PSA 접두사가 포함된 등급도 접두사를 한 번만 표시한다',()=>{
    const{container}=render(<CardCatalog cards={[card]}/>);

    expect(screen.getAllByText(/PSA 10/)).toHaveLength(2);
    expect(screen.queryByText(/PSA PSA 10/)).not.toBeInTheDocument();
    expect(container.querySelector('.catalog-image-viewport .catalog-card-image'))
      .toBeInTheDocument();
  });
});
