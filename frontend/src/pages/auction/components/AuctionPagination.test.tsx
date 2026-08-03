import {fireEvent,render,screen} from '@testing-library/react';
import {describe,expect,it,vi} from 'vitest';
import AuctionPagination from './AuctionPagination';

describe('AuctionPagination',()=>{
  it('현재 페이지와 이동 가능한 페이지 번호를 표시한다',()=>{
    const onPageChange=vi.fn();
    render(<AuctionPagination
      page={1}
      size={12}
      totalElements={61}
      onPageChange={onPageChange}
    />);

    expect(screen.getByRole('button',{name:'2페이지'})).toHaveAttribute('aria-current','page');

    fireEvent.click(screen.getByRole('button',{name:'3페이지'}));
    expect(onPageChange).toHaveBeenCalledWith(2);

    fireEvent.click(screen.getByRole('button',{name:'이전 페이지'}));
    expect(onPageChange).toHaveBeenCalledWith(0);

    fireEvent.click(screen.getByRole('button',{name:'다음 페이지'}));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('마지막 페이지에서는 다음 페이지 이동을 비활성화한다',()=>{
    render(<AuctionPagination
      page={5}
      size={12}
      totalElements={61}
      onPageChange={()=>{}}
    />);

    expect(screen.getByRole('button',{name:'다음 페이지'})).toBeDisabled();
    expect(screen.getByRole('button',{name:'6페이지'})).toHaveAttribute('aria-current','page');
  });
});
