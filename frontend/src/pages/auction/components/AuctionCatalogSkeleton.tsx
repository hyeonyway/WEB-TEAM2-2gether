export default function AuctionCatalogSkeleton({count=6,label='경매 목록을 불러오는 중'}:{count?:number;label?:string}){
  return <section className="card-grid auction-catalog-skeleton" aria-label={label} aria-busy="true">
    {Array.from({length:count},(_,index)=><article className="card-tile skeleton-card" key={index}>
      <div className="auction-skeleton-art skeleton-pulse"/>
      <div className="auction-skeleton-content">
        <div className="auction-skeleton-meta"><i className="skeleton-pulse"/><i className="skeleton-pulse"/></div>
        <i className="skeleton-line title skeleton-pulse"/>
        <i className="skeleton-line short skeleton-pulse"/>
        <i className="skeleton-line medium skeleton-pulse"/>
        <div className="auction-skeleton-values">
          {Array.from({length:4},(_,valueIndex)=><i className="skeleton-pulse" key={valueIndex}/>) }
        </div>
        <div className="auction-skeleton-actions"><i className="skeleton-pulse"/><i className="skeleton-pulse"/></div>
      </div>
    </article>)}
  </section>;
}
