import {authenticatedRequest} from './authenticatedRequest';
import {isMockApiEnabled} from './mockApiConfig';
import type {WishlistDto,WishlistResponseDto} from '../dto/wishlistDto';
import type {CardDto,CardResponseDto} from '../dto/auctionDto';
import {mapCard} from './auctionMapper';
import {fetchCards} from './auctionApi';

const MOCK_STORAGE_KEY='favorite-card-ids';

const mapWishlist=(dto:WishlistResponseDto):WishlistDto=>({
  id:dto.id,
  cardId:dto.card_id,
});

function readMockCardIds():number[]{
  try{
    const value=JSON.parse(localStorage.getItem(MOCK_STORAGE_KEY)??'[]');
    return Array.isArray(value)?value.filter(Number.isInteger):[];
  }catch{
    return [];
  }
}

function writeMockCardIds(cardIds:number[]):void{
  localStorage.setItem(MOCK_STORAGE_KEY,JSON.stringify(cardIds));
}

export async function fetchWishlists():Promise<WishlistDto[]>{
  if(isMockApiEnabled())return readMockCardIds().map(cardId=>({id:cardId,cardId}));
  const response=await authenticatedRequest<WishlistResponseDto[]>('/api/wishlists');
  return response.map(mapWishlist);
}

export async function fetchWishlistCards():Promise<CardDto[]>{
  if(isMockApiEnabled()){
    const favoriteIds=new Set(readMockCardIds());
    return (await fetchCards({keyword:'',psaGrade:null})).filter(card=>favoriteIds.has(card.id));
  }
  const response=await authenticatedRequest<CardResponseDto[]>('/api/wishlists/cards');
  return response.map(mapCard);
}

export async function addWishlist(cardId:number):Promise<WishlistDto>{
  if(isMockApiEnabled()){
    const cardIds=readMockCardIds();
    if(!cardIds.includes(cardId))writeMockCardIds([...cardIds,cardId]);
    return {id:cardId,cardId};
  }
  const response=await authenticatedRequest<WishlistResponseDto>('/api/wishlists',{
    method:'POST',
    body:JSON.stringify({cardId}),
  });
  return mapWishlist(response);
}

export async function removeWishlist(cardId:number):Promise<void>{
  if(isMockApiEnabled()){
    writeMockCardIds(readMockCardIds().filter(id=>id!==cardId));
    return;
  }
  await authenticatedRequest<void>(`/api/wishlists/${cardId}`,{method:'DELETE'});
}
