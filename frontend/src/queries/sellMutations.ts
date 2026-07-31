import {mutationOptions} from '@tanstack/react-query';
import {fetchCards} from '../api/auctionApi';
import {createAuction,uploadSellImages} from '../api/sellApi';
import type {RegisterAuctionRequestDto} from '../dto/sellDto';

export const sellMutations={
  register:()=>mutationOptions({
    mutationKey:['sell','register'],
    mutationFn:async(request:RegisterAuctionRequestDto)=>{
      const cards=await fetchCards({keyword:request.form.cardName,psaGrade:null});
      const card=cards.find(candidate=>candidate.name.trim()===request.form.cardName.trim());
      if(!card)throw new Error('등록할 카드 정보를 찾을 수 없습니다.');
      const photos=await uploadSellImages(request.photos);
      return createAuction({
        itemId:card.id,
        form:request.form,
        photos,
        psaCertification:request.psaCertification,
      });
    },
  }),
};
