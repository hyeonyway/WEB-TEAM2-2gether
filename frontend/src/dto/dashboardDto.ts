import type {AuctionStatus,MyBidStatus} from './auctionDto';

export type DashboardAuctionResponseDto={
  id:number;
  seller_id:number;
  card:{
    id:number;
    name:string;
    psa_grade:string|null;
    language:string|null;
    thumbnail_url:string|null;
  };
  start_price:number;
  current_price:number;
  bid_increment:number;
  bid_count:number;
  ends_at:string;
  status:AuctionStatus;
  version:number;
  my_bid_status:MyBidStatus;
  my_bid_amount:number|null;
};
