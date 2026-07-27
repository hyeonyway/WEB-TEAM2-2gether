export default function CardCatalogSkeleton({count=6}:{count?:number}){
  return <section className="catalog-grid catalog-skeleton" aria-label="카드 목록을 불러오는 중" aria-busy="true">
    {Array.from({length:count},(_,index)=><div className="catalog-card skeleton-card" key={index}>
      <div className="skeleton-art skeleton-pulse"/>
      <div className="skeleton-body">
        <i className="skeleton-line short skeleton-pulse"/>
        <i className="skeleton-line title skeleton-pulse"/>
        <i className="skeleton-line medium skeleton-pulse"/>
        <div className="skeleton-values">
          <i className="skeleton-pulse"/>
          <i className="skeleton-pulse"/>
        </div>
      </div>
    </div>)}
  </section>;
}
