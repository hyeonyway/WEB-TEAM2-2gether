import {request} from './httpClient';
import {fetchMockAuctions,fetchMockCards} from './mockAuctionApi';
import {mapAuction,mapCard} from './auctionMapper';
import type {AuctionDto,AuctionListRequestDto,AuctionResponseDto,CardDto,CardListRequestDto,CardResponseDto,PageResponseDto} from '../dto/auctionDto';

const useMock=import.meta.env.VITE_USE_MOCK_API==='true';
const params=(query:CardListRequestDto)=>new URLSearchParams({keyword:query.keyword,...(query.psaGrade===null?{}:{psaGrade:String(query.psaGrade)})});

export async function fetchCards(query:CardListRequestDto):Promise<CardDto[]>{
  if(useMock)return fetchMockCards(query);
  const response=await request<PageResponseDto<CardResponseDto>>(`/api/cards?${params(query)}`);
  return response.content.map(mapCard);
}

export async function fetchAuctions(query:AuctionListRequestDto):Promise<AuctionDto[]>{
  if(useMock)return fetchMockAuctions(query);
  const search=params(query);search.set('sort',query.sort);
  const response=await request<PageResponseDto<AuctionResponseDto>>(`/api/auctions?${search}`);
  return response.content.map(mapAuction);
}
