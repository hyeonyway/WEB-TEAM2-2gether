import {ChevronLeft,ChevronRight} from 'lucide-react';

const MAX_VISIBLE_PAGES=5;

type AuctionPaginationProps={
  page:number;
  size:number;
  totalElements:number;
  onPageChange:(page:number)=>void;
};

export default function AuctionPagination({
  page,
  size,
  totalElements,
  onPageChange,
}:AuctionPaginationProps){
  const totalPages=Math.ceil(totalElements/size);
  if(totalPages<=1)return null;

  const visibleCount=Math.min(MAX_VISIBLE_PAGES,totalPages);
  const start=Math.max(0,Math.min(page-Math.floor(visibleCount/2),totalPages-visibleCount));
  const pages=Array.from({length:visibleCount},(_,index)=>start+index);

  return <nav className="auction-pagination" aria-label="경매 목록 페이지">
    <button
      type="button"
      className="auction-pagination-arrow"
      aria-label="이전 페이지"
      title="이전 페이지"
      disabled={page===0}
      onClick={()=>onPageChange(page-1)}
    ><ChevronLeft/></button>
    {pages.map(pageIndex=><button
      type="button"
      key={pageIndex}
      aria-label={`${pageIndex+1}페이지`}
      aria-current={pageIndex===page?'page':undefined}
      className={pageIndex===page?'active':undefined}
      onClick={()=>onPageChange(pageIndex)}
    >{pageIndex+1}</button>)}
    <button
      type="button"
      className="auction-pagination-arrow"
      aria-label="다음 페이지"
      title="다음 페이지"
      disabled={page>=totalPages-1}
      onClick={()=>onPageChange(page+1)}
    ><ChevronRight/></button>
  </nav>;
}
