import type {AuctionStatus} from './auctionDto';

export type AuctionEventType=
  |'AUCTION_CREATED'
  |'BID_PLACED'
  |'AUCTION_CLOSED';

type AuctionSnapshotEventDto={
  type:AuctionEventType;
  auction_id:number;
  card_id:number;
  card_name:string;
  card_psa_grade:string|null;
  card_language:string|null;
  card_thumbnail_url:string|null;
  seller_id:number;
  start_price:number;
  bid_increment:number;
  bid_count:number;
  ends_at:string;
  status:AuctionStatus;
  auction_version:number;
  occurred_at:string;
};

export type AuctionCreatedEventDto=AuctionSnapshotEventDto&{
  type:'AUCTION_CREATED';
  current_price:number;
};

export type BidPlacedEventDto=AuctionSnapshotEventDto&{
  type:'BID_PLACED';
  bidder_id:number;
  previous_bidder_id:number|null;
  bid_price:number;
  current_price:number;
};

export type AuctionClosedEventDto=AuctionSnapshotEventDto&{
  type:'AUCTION_CLOSED';
  winner_id:number|null;
  final_price:number;
  closed_at:string;
};

export type AuctionUpdatedEventDto=
  |AuctionCreatedEventDto
  |BidPlacedEventDto
  |AuctionClosedEventDto;

const eventTypes:ReadonlySet<string>=new Set<AuctionEventType>([
  'AUCTION_CREATED','BID_PLACED','AUCTION_CLOSED',
]);
const statuses:ReadonlySet<string>=new Set<AuctionStatus>([
  'SCHEDULED','OPEN','ENDING','ENDED','CANCELLED','FAILED',
]);

const isNumber=(value:unknown):value is number=>
  typeof value==='number'&&Number.isFinite(value);
const isNullableString=(value:unknown):value is string|null=>
  value===null||typeof value==='string';
const isNullableNumber=(value:unknown):value is number|null=>
  value===null||isNumber(value);

function isSnapshot(value:Record<string,unknown>){
  return isNumber(value.auction_id)
    &&isNumber(value.card_id)
    &&typeof value.card_name==='string'
    &&isNullableString(value.card_psa_grade)
    &&isNullableString(value.card_language)
    &&isNullableString(value.card_thumbnail_url)
    &&isNumber(value.seller_id)
    &&isNumber(value.start_price)
    &&isNumber(value.bid_increment)
    &&isNumber(value.bid_count)
    &&typeof value.ends_at==='string'
    &&typeof value.status==='string'
    &&statuses.has(value.status)
    &&isNumber(value.auction_version)
    &&typeof value.occurred_at==='string';
}

export function parseAuctionUpdatedEvent(data:string):AuctionUpdatedEventDto|null{
  try{
    const value=JSON.parse(data) as unknown;
    if(value===null||typeof value!=='object')return null;
    const event=value as Record<string,unknown>;
    if(typeof event.type!=='string'||!eventTypes.has(event.type)||!isSnapshot(event))return null;
    if(event.type==='AUCTION_CREATED'){
      return isNumber(event.current_price)?event as AuctionCreatedEventDto:null;
    }
    if(event.type==='BID_PLACED'){
      return isNumber(event.bidder_id)
        &&isNullableNumber(event.previous_bidder_id)
        &&isNumber(event.bid_price)
        &&isNumber(event.current_price)
        ?event as BidPlacedEventDto:null;
    }
    return isNullableNumber(event.winner_id)
      &&isNumber(event.final_price)
      &&typeof event.closed_at==='string'
      ?event as AuctionClosedEventDto:null;
  }catch{
    return null;
  }
}
