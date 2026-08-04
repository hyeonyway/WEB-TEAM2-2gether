export default function AuctionDetailSkeleton(){
  return <main
    className="auction-detail-shell auction-detail-skeleton"
    role="status"
    aria-label="경매 상세 정보를 불러오는 중"
    aria-busy="true"
  >
    <div className="auction-detail-layout">
      <section className="auction-detail-product">
        <div className="auction-image-gallery">
          <div className="auction-gallery-stage auction-detail-skeleton-stage">
            <i className="auction-detail-skeleton-card skeleton-pulse"/>
          </div>
          <div className="auction-detail-skeleton-thumbs">
            <i className="skeleton-pulse"/><i className="skeleton-pulse"/>
            <i className="skeleton-pulse"/><i className="skeleton-pulse"/>
          </div>
        </div>
      </section>
      <section className="auction-bid-panel auction-detail-skeleton-panel">
        <div className="auction-detail-skeleton-copy"><i className="skeleton-pulse"/><i className="skeleton-pulse"/><i className="skeleton-pulse"/></div>
        <i className="skeleton-pulse"/><i className="skeleton-pulse"/><i className="skeleton-pulse"/>
        <div><i className="skeleton-pulse"/><i className="skeleton-pulse"/><i className="skeleton-pulse"/></div>
        <i className="skeleton-pulse"/>
        <i className="skeleton-pulse"/>
        <div className="auction-detail-skeleton-rows"><i className="skeleton-pulse"/><i className="skeleton-pulse"/><i className="skeleton-pulse"/></div>
      </section>
    </div>
    <section className="auction-seller-post auction-detail-skeleton-post"><i className="skeleton-pulse"/><div><i className="skeleton-pulse"/><i className="skeleton-pulse"/><i className="skeleton-pulse"/></div></section>
  </main>;
}
